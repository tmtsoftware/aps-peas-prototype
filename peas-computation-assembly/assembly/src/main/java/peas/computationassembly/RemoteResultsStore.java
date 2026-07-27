package peas.computationassembly;

import peas.computationassembly.metadata.ComputationParameter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Remote implementation of ResultsStore.
 * Delegates to the APS PEAS Procedure Data Service via HTTP.
 *
 * All Source.RESULTS inputs are fetched in a single POST /getProcedureResultData call
 * before execution begins, and all Destination.RESULTS outputs are posted in a single
 * POST /storeProcedureComputationResults call after execution completes.
 *
 * iterationNumber is passed through to both service calls. Pass 0 if not applicable.
 */
public class RemoteResultsStore implements ResultsStore {

    private final ProcedureDataClient client;
    private int iterationNumber;

    // Local cache populated by prefetch(), drained by flush()
    private final Map<String, Object> inputCache  = new HashMap<>();
    private final Map<String, Object> outputCache = new HashMap<>();

    public RemoteResultsStore(ProcedureDataClient client) {
        this.client = client;
        this.iterationNumber = 0;
    }

    /** Set before each command execution if iteration tracking is needed. */
    public void setIterationNumber(int iterationNumber) {
        this.iterationNumber = iterationNumber;
    }

    /**
     * Fetches all Source.RESULTS input parameters from the service in one call
     * and caches them locally. Called by AbstractExecuteCommand before the resolution loop.
     */
    public void prefetch(String commandName, List<ComputationParameter> metadata, Map<String, String> referenceOverrides) throws Exception {
        if (referenceOverrides.isEmpty()) return;

        inputCache.clear();
        inputCache.putAll(client.fetchInputs(referenceOverrides, metadata, iterationNumber));
    }
    /**
     * Posts all Destination.RESULTS output parameters to the service in one call.
     * Called by AbstractExecuteCommand after the algorithm returns.
     */
    public void flush(String commandName, List<ComputationParameter> metadata) throws Exception {
        List<ComputationParameter> resultsOutputs = metadata.stream()
                .filter(p -> p.direction   == ComputationParameter.Direction.OUTPUT
                          && p.destination == ComputationParameter.Destination.RESULTS)
                .toList();

        if (outputCache.isEmpty() || resultsOutputs.isEmpty()) return;

        client.storeResults(commandName, outputCache, resultsOutputs, iterationNumber);
        outputCache.clear();
    }

    @Override
    public Object get(String key) {
        // Key format from AbstractExecuteCommand is "commandName.fieldName"
        // inputCache is keyed by fieldName only (as returned by the service)
        String fieldName = keyToFieldName(key);
        Object value = inputCache.get(fieldName);
        if (value == null) {
            throw new RuntimeException("RemoteResultsStore: no prefetched value for key ["
                    + key + "]. Ensure prefetch() was called before the resolution loop.");
        }
        return value;
    }

    @Override
    public void set(String key, Object value) {
        // Store by fieldName only to match what storeResults expects
        outputCache.put(keyToFieldName(key), value);
    }

    /** Extracts the fieldName from a "commandName.fieldName" key. */
    private String keyToFieldName(String key) {
        int dot = key.indexOf('.');
        return dot >= 0 ? key.substring(dot + 1) : key;
    }
}
