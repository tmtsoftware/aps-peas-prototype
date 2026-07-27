package peas.computationassembly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.http.javadsl.Http;
import org.apache.pekko.http.javadsl.model.ContentTypes;
import org.apache.pekko.http.javadsl.model.HttpRequest;
import org.apache.pekko.http.javadsl.model.HttpResponse;
import org.apache.pekko.http.javadsl.unmarshalling.Unmarshaller;
import peas.computationassembly.metadata.ComputationParameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * HTTP client for the APS PEAS Procedure Data Service.
 *
 * Implements two operations matching the OpenAPI spec:
 *
 *   POST /getProcedureResultData
 *     Request:  { procedureRunId, computationResultKeys: [{computationName, fieldName, iterationNumber?}] }
 *     Response: [ { key: {computationName, fieldName, iterationNumber?}, value: {type, dim1, dim2, encodedStringValue} } ]
 *
 *   POST /storeProcedureComputationResults
 *     Request:  { procedureRunId, keyValuePairList: [
 *                   { key: {procedureRunId?, iterationNumber?, computationName, fieldName},
 *                     value: {type, dim1, dim2, encodedStringValue} } ] }
 *
 * GenericValue encoding:
 *   scalar:   encodedStringValue="36.5",         dim1=1,    dim2=1
 *   1D array: encodedStringValue="1.0, 2.0, ...", dim1=len, dim2=1
 *   2D array: encodedStringValue="r0c0, r0c1; r1c0, r1c1", dim1=cols, dim2=rows
 */
public class ProcedureDataClient {

    private final Http http;
    private final ActorSystem<?> system;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final int procedureRunId;

    private static final long TIMEOUT_SECONDS = 10;

    public ProcedureDataClient(ActorSystem<?> system, String host, int port, int procedureRunId) {
        this.system = system;
        this.http = Http.get(system);
        this.mapper = new ObjectMapper();
        this.baseUrl = "http://" + host + ":" + port;
        this.procedureRunId = procedureRunId;
    }

    // -------------------------------------------------------------------------
    // FETCH: POST /getProcedureResultData
    // Returns a map of fieldName -> deserialised Java value.
    // -------------------------------------------------------------------------
    public Map<String, Object> fetchInputs(Map<String, String> references,
                                           List<ComputationParameter> metadata,
                                           int iterationNumber) throws Exception {
        // Build request body
        ObjectNode root = mapper.createObjectNode();
        root.put("procedureRunId", procedureRunId);
        ArrayNode keys = root.putArray("computationResultKeys");

        // Map refFieldName -> formalName for response decoding
        Map<String, String> refFieldToFormalName = new HashMap<>();
        // Map refFieldName -> ComputationParameter for decoding
        Map<String, ComputationParameter> refFieldToParam = new HashMap<>();

        for (Map.Entry<String, String> entry : references.entrySet()) {
            String formalName         = entry.getKey();
            String[] parts            = entry.getValue().split("\\.");
            String refComputationName = parts[0];
            String refFieldName       = parts[1];

            refFieldToFormalName.put(refFieldName, formalName);

            ComputationParameter param = metadata.stream()
                    .filter(p -> p.name.equals(formalName))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No metadata for parameter [" + formalName + "]"));
            refFieldToParam.put(refFieldName, param);

            ObjectNode key = keys.addObject();
            key.put("computationName", refComputationName);
            key.put("fieldName", refFieldName);
            if (iterationNumber > 0) key.put("iterationNumber", iterationNumber);
        }

        HttpResponse response = http.singleRequest(
                        HttpRequest.POST(baseUrl + "/getProcedureResultData")
                                .withEntity(ContentTypes.APPLICATION_JSON, mapper.writeValueAsString(root)))
                .toCompletableFuture()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!response.status().isSuccess()) {
            response.discardEntityBytes(system);
            throw new RuntimeException("getProcedureResultData failed: HTTP " + response.status());
        }

        JsonNode responseArray = mapper.readTree(entityToString(response));
        Map<String, Object> result = new HashMap<>();

        for (JsonNode kvPair : responseArray) {
            String refFieldName = kvPair.get("key").get("fieldName").asText();
            String formalName   = refFieldToFormalName.get(refFieldName);
            ComputationParameter param = refFieldToParam.get(refFieldName);
            result.put(formalName, decodeGenericValue(kvPair.get("value"), param));
        }

        return result;
    }
    // -------------------------------------------------------------------------
    // STORE: POST /storeProcedureComputationResults
    // Posts all output parameters as a ComputationKeyValuePairList in one call.
    // -------------------------------------------------------------------------
    public void storeResults(String commandName,
                             Map<String, Object> results,
                             List<ComputationParameter> metadata,
                             int iterationNumber) throws Exception {

        Map<String, ComputationParameter> metaByName = new HashMap<>();
        for (ComputationParameter p : metadata) metaByName.put(p.name, p);

        ObjectNode root = mapper.createObjectNode();
        root.put("procedureRunId", procedureRunId);
        ArrayNode kvList = root.putArray("keyValuePairList");

        for (Map.Entry<String, Object> entry : results.entrySet()) {
            String fieldName = entry.getKey();
            ComputationParameter param = metaByName.get(fieldName);
            if (param == null) throw new RuntimeException(
                    "No metadata found for result parameter [" + fieldName + "]");

            ObjectNode kvPair = kvList.addObject();

            ObjectNode key = kvPair.putObject("key");
            key.put("procedureRunId", procedureRunId);
            key.put("computationName", commandName);
            key.put("fieldName", fieldName);
            if (iterationNumber > 0) key.put("iterationNumber", iterationNumber);

            kvPair.set("value", encodeGenericValue(entry.getValue(), param));
        }

        HttpResponse response = http.singleRequest(
                        HttpRequest.POST(baseUrl + "/storeProcedureComputationResults")
                                .withEntity(ContentTypes.APPLICATION_JSON, mapper.writeValueAsString(root)))
                .toCompletableFuture()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        response.discardEntityBytes(system);

        if (!response.status().isSuccess()) {
            throw new RuntimeException("storeProcedureComputationResults failed for ["
                    + commandName + "]: HTTP " + response.status());
        }
    }

    // -------------------------------------------------------------------------
    // Encode Java value -> GenericValue JSON node
    // -------------------------------------------------------------------------
    private ObjectNode encodeGenericValue(Object value, ComputationParameter param) {
        ObjectNode node = mapper.createObjectNode();
        int[] dims = param.dimensions;

        if (dims == null || dims.length == 0) {
            node.put("type", typeName(param.type));
            node.put("dim1", 1);
            node.put("dim2", 1);
            node.put("encodedStringValue", value.toString());

        } else if (dims.length == 1 && param.type == float.class) {
            node.put("type", "float");
            node.put("dim1", dims[0]);
            node.put("dim2", 1);
            node.put("encodedStringValue", floatArrayToString((float[]) value));

        } else if (dims.length == 1 && param.type == int.class) {
            node.put("type", "integer");
            node.put("dim1", dims[0]);
            node.put("dim2", 1);
            node.put("encodedStringValue", intArrayToString((int[]) value));

        } else if (dims.length == 2 && param.type == float.class) {
            // dim1 = fast index = columns, dim2 = rows
            node.put("type", "float");
            node.put("dim1", dims[1]);
            node.put("dim2", dims[0]);
            node.put("encodedStringValue", float2dArrayToString((float[][]) value));

        } else {
            throw new IllegalArgumentException(
                    "Unsupported type/dimensions for encoding: " + param.type + ", dims=" + dims.length);
        }

        return node;
    }

    // -------------------------------------------------------------------------
    // Decode GenericValue JSON node -> Java value
    // -------------------------------------------------------------------------
    private Object decodeGenericValue(JsonNode genericValue, ComputationParameter param) {
        int dim1 = genericValue.get("dim1").asInt();
        int dim2 = genericValue.get("dim2").asInt();
        String encoded = genericValue.get("encodedStringValue").asText();
        int[] dims = param.dimensions;

        if (dims == null || dims.length == 0) {
            if (param.type == float.class) return Float.parseFloat(encoded.trim());
            if (param.type == int.class)   return Integer.parseInt(encoded.trim());
            throw new IllegalArgumentException("Unsupported scalar type: " + param.type);

        } else if (dims.length == 1) {
            String[] tokens = encoded.split(",");
            if (param.type == float.class) {
                float[] arr = new float[dim1];
                for (int i = 0; i < dim1; i++) arr[i] = Float.parseFloat(tokens[i].trim());
                return arr;
            }
            if (param.type == int.class) {
                int[] arr = new int[dim1];
                for (int i = 0; i < dim1; i++) arr[i] = Integer.parseInt(tokens[i].trim());
                return arr;
            }

        } else if (dims.length == 2 && param.type == float.class) {
            // dim2 = rows, dim1 = columns
            float[][] arr = new float[dim2][dim1];
            String[] rows = encoded.split(";");
            for (int i = 0; i < dim2; i++) {
                String[] cols = rows[i].trim().split(",");
                for (int j = 0; j < dim1; j++) arr[i][j] = Float.parseFloat(cols[j].trim());
            }
            return arr;
        }

        throw new IllegalArgumentException(
                "Unsupported type/dimension for decoding: " + param.type + ", dims=" + dims.length);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String typeName(Class<?> type) {
        if (type == float.class)  return "float";
        if (type == int.class)    return "integer";
        if (type == double.class) return "double";
        return type.getSimpleName().toLowerCase();
    }

    private String floatArrayToString(float[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private String intArrayToString(int[] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    private String float2dArrayToString(float[][] arr) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append("; ");
            sb.append(floatArrayToString(arr[i]));
        }
        return sb.toString();
    }

    private String entityToString(HttpResponse response) throws Exception {
        return Unmarshaller.entityToString()
                .unmarshal(response.entity(), system)
                .toCompletableFuture()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }
}
