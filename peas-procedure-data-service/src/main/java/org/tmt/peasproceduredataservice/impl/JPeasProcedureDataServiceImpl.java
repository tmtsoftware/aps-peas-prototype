package org.tmt.peasproceduredataservice.impl;

import csw.event.api.javadsl.IEventService;
import csw.event.api.javadsl.IEventSubscription;
import csw.params.core.generics.Key;
import csw.params.core.generics.Parameter;
import csw.params.events.Event;
import csw.params.events.EventKey;
import csw.params.events.EventName;
import csw.params.events.SystemEvent;
import csw.params.javadsl.JKeyType;
import csw.prefix.javadsl.JSubsystem;
import csw.prefix.models.Prefix;
import esw.http.template.wiring.JCswServices;
import org.tmt.peasproceduredataservice.core.models.*;
import org.tmt.peasproceduredataservice.db.ProcedureDbService;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class JPeasProcedureDataServiceImpl {

    private final JCswServices jCswServices;

    private final ProcedureDbService procedureDbService;



    // In-memory store keyed by StoreKey.
    // Prototype implementation — does not persist to database.
    private final ConcurrentHashMap<StoreKey, ComputationKeyValuePair> store = new ConcurrentHashMap<>();

    // Held so the subscription is not garbage collected
    private IEventSubscription exposureStoreCompletedSubscription;

    private record StoreKey(
            int procedureRunId,
            String computationName,
            String fieldName,
            Optional<Integer> iterationNumber
    ) {}

    // ── Event constants ───────────────────────────────────────────────────────

    // The prefix of the component that PUBLISHES exposureStoreCompleted.
    // The Exposure Service subscribes to this event — it does not publish it.
    // Update this prefix to match the actual publishing component once known.
    private static final Prefix EXPOSURE_STORE_PUBLISHER_PREFIX =
            new Prefix(JSubsystem.APS, "exposureAssembly");

    private static final EventName EXPOSURE_STORE_COMPLETED =
            new EventName("exposureStoreCompleted");

    private static final Key<String> FILENAME_KEY =
            JKeyType.StringKey().make("filename");

    // ── Constructor ───────────────────────────────────────────────────────────

    public JPeasProcedureDataServiceImpl(JCswServices jCswServices, ProcedureDbService procedureDbService) {
        this.jCswServices = jCswServices;
        this.procedureDbService = procedureDbService;

        // pre-fill store:

        // in out test, we have these pre-initialized from a 'previous step'
        String centroidOffsetsX = "-0.172f, -0.783f, -0.736f, 1.541f, 0.937f, 0.688f, -0.573f, -1.473f, -1.925f, -1.406f, " +
                "-1.861f, -0.151f, 0.814f, 0.688f, 2.336f, 2.152f, 2.830f, 1.567f, -0.722f, -2.009f, -2.523f, -3.187f, " +
                "-2.840f, -2.990f, -3.044f, -1.716f, 5.332f, -5.395f, 1.456f, 1.453f, 2.334f, 2.370f, 1.381f, 2.058f, 2.213f, 1.357f";

        String centroidOffsetsY = "0.770f, 0.790f, -0.227f, -0.840f, -0.596f, -0.002f, 2.184f, 1.379f, 1.673f, 0.891f, -1.017f, " +
                "-1.913f, -2.428f, -1.720f, -1.569f, -0.760f, -1.677f, 1.239f, 3.146f, 3.277f, 2.767f, 2.677f, 1.470f, -0.154f, " +
                "-1.825f, -2.638f, 3.006f, -9.161f, -2.118f, -2.004f, 0.162f, -2.130f, -0.984f, 3.866f, 1.741f, 2.723f";

        ComputationKeyValuePairKey keyX = new ComputationKeyValuePairKey(Optional.of(1), Optional.empty(), "centroidOffsets",
                "centroidOffsetsX");
        GenericValue valueX = new GenericValue("float", 36, 0, centroidOffsetsX);
        ComputationKeyValuePair kvpX = new ComputationKeyValuePair(keyX, valueX);
        StoreKey storeKeyX = new StoreKey(1,"centroidOffsets", "centroidOffsetsX", Optional.empty());
        store.put(storeKeyX, kvpX);

        ComputationKeyValuePairKey keyY = new ComputationKeyValuePairKey(Optional.of(1), Optional.empty(), "centroidOffsets",
                "centroidOffsetsY");
        GenericValue valueY = new GenericValue("float", 36, 0, centroidOffsetsY);
        ComputationKeyValuePair kvpY = new ComputationKeyValuePair(keyY, valueY);
        StoreKey storeKeyY = new StoreKey(1,"centroidOffsets", "centroidOffsetsY", Optional.empty());
        store.put(storeKeyY, kvpY);



    }
    // ── Event subscription ────────────────────────────────────────────────────

    /**
     * Subscribes to the exposureStoreCompleted SystemEvent.
     *
     * Note: this is example code for the Exposure Service which will need to subscribe
     * to the exposureStoreCompleted event.  This code section merely demonstrates how this
     * can be done.
     * This service (Procedure Data Service) does not use this code, and it would be removed when
     * building production code.
     *
     * Called once from PeasProcedureDataServiceWiring after construction.
     */
    public void subscribeToExposureEvents() {
        IEventService eventService = jCswServices.eventService();

        EventKey exposureStoreCompletedKey = new EventKey(
                EXPOSURE_STORE_PUBLISHER_PREFIX,
                EXPOSURE_STORE_COMPLETED
        );

        exposureStoreCompletedSubscription = eventService
                .defaultSubscriber()
                .subscribeAsync(
                        Collections.singleton(exposureStoreCompletedKey),
                        this::handleExposureStoreCompleted
                );
    }

    private CompletableFuture<String> handleExposureStoreCompleted(Event event) {
        if (!(event instanceof SystemEvent systemEvent)) {
            return CompletableFuture.completedFuture("ignored — not a SystemEvent");
        }

        Optional<Parameter<String>> filenameParam = systemEvent.jGet(FILENAME_KEY);

        if (filenameParam.isEmpty()) {
            return CompletableFuture.completedFuture("ignored — filename parameter missing");
        }

        String filename = filenameParam.get().head();

        // TODO: store or act on the filename as the integration requires.
        // For now, log it via the CSW logging service.
        jCswServices.loggerFactory()
                .getLogger(getClass())
                .info("exposureStoreCompleted received: filename = " + filename);

        return CompletableFuture.completedFuture(filename);
    }

    // ── Existing ──────────────────────────────────────────────────────────────

    public CompletableFuture<GreetResponse> sayBye() {
        return CompletableFuture.completedFuture(new GreetResponse("Bye!!!"));
    }

    // ── POST /storeProcedureComputationResults ────────────────────────────────

    public CompletableFuture<Void> storeProcedureComputationResults(ComputationKeyValuePairList request) {
        jCswServices.loggerFactory()
                .getLogger(getClass())
                .info("storeProcedureComputationResults");
        for (ComputationKeyValuePair kvp : request.keyValuePairList()) {
            int runId = kvp.key().procedureRunId().orElse((Integer) request.procedureRunId());
            StoreKey key = new StoreKey(
                    runId,
                    kvp.key().computationName(),
                    kvp.key().fieldName(),
                    kvp.key().iterationNumber()
            );
            store.put(key, kvp);
        }
        // Fire-and-forget async DB write — does not block the HTTP response
        procedureDbService.insertResultsAsync(request);

        return CompletableFuture.completedFuture(null);
    }

    // ── POST /getProcedureResultData ──────────────────────────────────────────

    public CompletableFuture<List<ComputationKeyValuePair>> getProcedureResultData(GetProcedureResultDataRequest request) {
        jCswServices.loggerFactory()
                .getLogger(getClass())
                .info("getProcedureResultData: " + request.computationResultKeys().get(0).computationName() + ", " + request.computationResultKeys().get(0).fieldName());
        List<ComputationKeyValuePair> results = new ArrayList<>();
        for (var resultKey : request.computationResultKeys()) {
            StoreKey key = new StoreKey(
                    request.procedureRunId(),
                    resultKey.computationName(),
                    resultKey.fieldName(),
                    resultKey.iterationNumber()
            );
            ComputationKeyValuePair found = store.get(key);
            if (found != null) results.add(found);
        }
        return CompletableFuture.completedFuture(results);
    }
}