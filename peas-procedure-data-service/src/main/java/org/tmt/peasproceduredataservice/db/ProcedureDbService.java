package org.tmt.peasproceduredataservice.db;

import csw.database.DatabaseServiceFactory;
import csw.logging.api.javadsl.ILogger;
import csw.logging.client.javadsl.JLoggerFactory;
import esw.http.template.wiring.JCswServices;
import org.apache.pekko.actor.typed.ActorSystem;
import org.jooq.DSLContext;
import org.tmt.peasproceduredataservice.core.models.ComputationKeyValuePair;
import org.tmt.peasproceduredataservice.core.models.ComputationKeyValuePairList;
import org.tmt.peasproceduredataservice.db.generated.tables.Procedureresult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Owns the jOOQ DSLContext obtained from the CSW DatabaseServiceFactory.
 * The DSL is initialised once at service startup via initAsync().
 */
public class ProcedureDbService {

    private final ILogger       log;
    private final ExecutorService dbExecutor;
    private volatile DSLContext dsl;   // set once during startup

    // ── Constructor ───────────────────────────────────────────────────────────

    public ProcedureDbService(JLoggerFactory loggerFactory) {
        this.log        = loggerFactory.getLogger(getClass());
        // Dedicated thread pool keeps DB I/O off the Pekko dispatcher
        this.dbExecutor = Executors.newFixedThreadPool(4);
    }

    // ── Startup — call once from wiring after construction ───────────────────

    /**
     * Initialises the DSLContext using the CSW DatabaseServiceFactory.
     * dbName must match the [db.{dbName}] entry in application.conf / CSW config.
     * Returns a future that completes when the DSL is ready.
     */
    public CompletableFuture<Void> init(ActorSystem<?> system,
                                        JCswServices jCswServices,
                                        String dbName) {
        DatabaseServiceFactory dbFactory = new DatabaseServiceFactory(system);

        return dbFactory
                .jMakeDsl(jCswServices.locationService(), dbName)
                .thenAccept(dslContext -> {
                    this.dsl = dslContext;
                    log.info("ProcedureDbService: DSLContext ready for db=" + dbName);
                });
    }

    // ── Async fire-and-forget insert ──────────────────────────────────────────

    /**
     * Inserts all rows from the request into ProcedureResult asynchronously.
     * Returns immediately — the HTTP response does NOT wait for DB completion.
     * Errors are logged but not propagated to the caller.
     */
    public void insertResultsAsync(ComputationKeyValuePairList request) {
        if (dsl == null) {
            log.error("insertResultsAsync called before DSLContext is ready — dropping request");
            return;
        }
        CompletableFuture.runAsync(() -> insertResults(request), dbExecutor)
                .exceptionally(ex -> {
                    log.error("Async DB insert failed for procedureRunId="
                            + request.procedureRunId() + ": " + ex.getMessage());
                    return null;
                });
    }

    // ── Synchronous batch insert (runs on dbExecutor thread) ─────────────────

    private void insertResults(ComputationKeyValuePairList request) {
        var PR = Procedureresult.PROCEDURERESULT;

        var batch = dsl.batch(
                dsl.insertInto(PR,
                                PR.PROCEDUREID,
                                PR.PROCEDUREITERATIONID,
                                PR.COMPUTATIONNAME,
                                PR.FIELDNAME,
                                PR.DATATYPE,
                                PR.DIM1,
                                PR.DIM2,
                                PR.ENCODEDSTRINGVALUE)
                        .values((Long) null, null, "", "", "", 0, 0, "")  // template row
        );

        for (ComputationKeyValuePair kvp : request.keyValuePairList()) {
            int runId = kvp.key().procedureRunId()
                    .orElse((Integer) request.procedureRunId());

            Long iterationId = kvp.key().iterationNumber().isPresent()
                    ? kvp.key().iterationNumber().get().longValue()
                    : null;

            batch.bind(
                    (long) runId,
                    iterationId,
                    kvp.key().computationName(),
                    kvp.key().fieldName(),
                    kvp.value().type(),
                    kvp.value().dim1(),
                    kvp.value().dim2(),
                    kvp.value().encodedStringValue()
            );
        }

        int[] counts = batch.execute();
        log.debug("Inserted " + counts.length + " ProcedureResult rows "
                + "for procedureRunId=" + request.procedureRunId());
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    public void close() {
        dbExecutor.shutdown();
        try {
            dbExecutor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("ProcedureDbService shut down.");
    }
}
