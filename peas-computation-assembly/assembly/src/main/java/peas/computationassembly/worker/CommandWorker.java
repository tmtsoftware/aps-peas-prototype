package peas.computationassembly.worker;

import org.apache.pekko.actor.typed.Behavior;
import org.apache.pekko.actor.typed.javadsl.ActorContext;
import org.apache.pekko.actor.typed.javadsl.Behaviors;
import peas.computationassembly.AlgorithmLibrary;
import peas.computationassembly.LocalResultsStore;
import peas.computationassembly.ProcedureDataClient;
import peas.computationassembly.RemoteResultsStore;
import peas.computationassembly.ResultsStore;
import peas.computationassembly.commands.WorkerCommand;
import csw.command.client.messages.TopLevelActorMessage;
import csw.framework.models.JCswContext;
import csw.params.commands.CommandResponse;
import csw.params.commands.Result;

public class CommandWorker {

    // -------------------------------------------------------------------------
    // Toggle to switch between local cache and remote procedure data service.
    // -------------------------------------------------------------------------
    private static final boolean USE_REMOTE_RESULTS_STORE = true;

    private static final String PROCEDURE_SERVICE_HOST = "192.168.0.151";
    private static final int    PROCEDURE_SERVICE_PORT = 8084;

    // procedureRunId is assigned by the service via createNewProcedureRun.
    // For the prototype a fixed value is fine.
    private static final int PROCEDURE_RUN_ID = 1;

    /**
     * Builds and returns the ResultsStore to be held by the handler.
     * The handler passes it into each Execute* constructor at command dispatch time.
     */
    public static ResultsStore buildResultsStore(ActorContext<TopLevelActorMessage> ctx) {
        if (USE_REMOTE_RESULTS_STORE) {
            ProcedureDataClient client = new ProcedureDataClient(
                    ctx.getSystem(),
                    PROCEDURE_SERVICE_HOST,
                    PROCEDURE_SERVICE_PORT,
                    PROCEDURE_RUN_ID
            );
            return new RemoteResultsStore(client);
        } else {
            return new LocalResultsStore();
        }
    }

    /**
     * Creates the worker actor behavior. The ResultsStore is already wired into
     * each Execute* instance before it is sent as a message to this actor.
     */
    public static Behavior<WorkerCommand> create(JCswContext cswCtx) {
        return Behaviors.receive(WorkerCommand.class)
                .onMessage(WorkerCommand.class,
                        command -> execute(cswCtx, command))
                .build();
    }

    private static Behavior<WorkerCommand> execute(JCswContext cswCtx, WorkerCommand command) {
        try {
            AlgorithmLibrary library = new AlgorithmLibrary(cswCtx);

            double t1 = System.nanoTime();
            Result result = command.execute(library);
            double t2 = System.nanoTime();

            cswCtx.loggerFactory()
                    .getLogger(CommandWorker.class)
                    .info("Finished in " + (t2 - t1) / 1_000_000.0 + " ms");

            cswCtx.commandResponseManager()
                    .updateCommand(new CommandResponse.Completed(command.runId(), result));

        } catch (Exception e) {
            cswCtx.commandResponseManager()
                    .updateCommand(new CommandResponse.Error(command.runId(), e.getMessage()));
        }

        return Behaviors.same();
    }
}
