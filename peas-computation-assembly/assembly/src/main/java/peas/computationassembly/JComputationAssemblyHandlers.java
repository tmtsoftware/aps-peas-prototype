package peas.computationassembly;

import csw.config.api.ConfigData;
import csw.config.api.javadsl.IConfigClientService;

import org.apache.pekko.actor.typed.ActorRef;
import org.apache.pekko.actor.typed.javadsl.ActorContext;

import csw.command.client.messages.TopLevelActorMessage;
import csw.framework.javadsl.JComponentHandlers;
import csw.framework.models.JCswContext;
import csw.location.api.models.TrackingEvent;
import csw.logging.api.javadsl.ILogger;
import csw.params.commands.*;
import csw.params.core.models.Id;
import csw.time.core.models.UTCTime;

import com.typesafe.config.Config;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import peas.computationassembly.worker.CommandWorker;
import peas.computationassembly.commands.*;
import org.tmt.aps.peas.lang.interop.RetVal;

public class JComputationAssemblyHandlers extends JComponentHandlers {

    private final JCswContext cswCtx;
    private final ILogger log;
    private final ActorContext<TopLevelActorMessage> actorContext;
    private final ActorRef<WorkerCommand> workerActor;
    private final ResultsStore resultsStore;

    private IConfigClientService configClient;

    public JComputationAssemblyHandlers(ActorContext<TopLevelActorMessage> ctx, JCswContext cswCtx) {
        super(ctx, cswCtx);
        this.cswCtx = cswCtx;
        this.log = cswCtx.loggerFactory().getLogger(getClass());
        this.actorContext = ctx;

        System.loadLibrary("peas");

        configClient = cswCtx.configClientService();

        // Build the ResultsStore once — shared across all command dispatches
        this.resultsStore = CommandWorker.buildResultsStore(ctx);

        this.workerActor = actorContext.spawn(
                CommandWorker.create(cswCtx),
                "CommandWorker"
        );
    }

    // ================== Component Lifecycle Hooks ==================

    @Override
    public void initialize() {
        IConfigClientService configClient = cswCtx.configClientService();
        HoconReader hoconReader = new HoconReader();

        String[] filenames = {
                "APS_DB_FS_6_rectangular_array",
                "APS_DB_FS_6_rings_global_xy_scaled",
                "APS_DB_FS_6_rings_local_xy_unscaled",
                "APS_DB_M1CS_modes",
                "APS_DB_PH_2_periph_subaps_vs_segment",
                "APS_DB_PH_params_2_subap",
                "APS_DB_PH_triangles",
                "APS_DB_PH_closure_triples",
                "APS_DB_emult_sing_values",
                "APS_DB_given_1_subaps_find_corres_2",
                "APS_DB_given_2_subaps_find_corres_1",
                "APS_DB_optimal_FI_freqs_8192_6_rings",
                "APS_DB_ref_def_tmt_8192_FS_6",
                "APS_DB_segment_colors",
                "APS_DB_segment_sensor_numbers_2_subap",
                "APS_DB_actuators_xy",
                "APS_DB_seg_ctrs",
                "APS_DB_sensor_data",
                "APS_DB_ungapped_vertices",
                "APS_DB_m1cs_sensor_numbering",
                "APS_DB_Amatrix_Leff"
        };

        /*
        for (String filename : filenames) {
            Path filePath = Paths.get("tmt/aps/db/" + filename + ".conf");
            try {
                Optional<ConfigData> maybeConfigData = configClient.getActive(filePath).get();
                Config config = maybeConfigData
                        .orElseThrow(() -> new RuntimeException("Config file not found: " + filePath))
                        .toJConfigObject(actorContext.getSystem())
                        .get();
                hoconReader.readFile(config, filename);
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException("Failed to load: " + filePath, e);
            }
        }
        */
        
        log.info("Initializing PeasComputationAssembly...");
        System.loadLibrary("peas");

        AlgorithmLibrary algorithmLibrary = new AlgorithmLibrary(cswCtx);

        int stepCount = 11;
        float stepSizeMicrons = 20.0f;
        float stepSizeNm = stepSizeMicrons * 1000.0f;
        RetVal retVal = new RetVal();

        log.info("computation.starting: colorStep");

        try {

            double t1 = System.nanoTime();
            float[][] colorsteps = new float[36][3];
            algorithmLibrary.colorStep(stepCount, stepSizeNm, colorsteps);
            double t2 = System.nanoTime();

            for (int i=0; i<stepCount+1; i++){
                for (int j = 0; j < 3; j++){
                    log.info("i,j = " + i + "," + j + "   colorstep = " + colorsteps[i][j]);
                }
            }
            log.info("computation.success colorStep: time to execute = " + (t2-t1)/1000000.0 + " ms");

        } catch (Exception e) {
            log.error(e.getMessage());
        }

        // build an 8120 x 8120 float array with random data
        int size = 8120;
        float[][] frame = new float[size][size];
        Random random = new Random();

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                frame[i][j] = random.nextFloat() * 10000f;
            }
        }

        int[] numFilledBoxes = new int[1];

        try {
            double t1 = System.nanoTime();
            algorithmLibrary.testApsFrame(frame, numFilledBoxes);
            double t2 = System.nanoTime();
            log.info("computation.success testApsFrame: time to execute = " + (t2 - t1) / 1000000.0 + " ms");
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        System.out.println("numFilledBoxes = " + numFilledBoxes[0]);
    }

    @Override public void onShutdown() {}
    @Override public void onLocationTrackingEvent(TrackingEvent trackingEvent) {}
    @Override public void onGoOffline() {}
    @Override public void onGoOnline() {}
    @Override public void onDiagnosticMode(UTCTime startTime, String hint) {}
    @Override public void onOperationsMode() {}
    @Override public void onOneway(Id runId, ControlCommand controlCommand) {}

    // ================== Command Validation ==================

    @Override
    public CommandResponse.ValidateCommandResponse validateCommand(Id runId, ControlCommand controlCommand) {
        String commandName = controlCommand.commandName().name();
        switch (commandName) {
            case "colorStep", "ttOffsetsToActs", "decomposeActs":
                return new CommandResponse.Accepted(runId);
            default:
                return new CommandResponse.Invalid(
                        runId,
                        new CommandIssue.UnsupportedCommandIssue("Unsupported command: " + commandName)
                );
        }
    }

    // ================== Command Submission ==================

    @Override
    public CommandResponse.SubmitResponse onSubmit(Id runId, ControlCommand controlCommand) {

        log.info("Handling command: " + controlCommand.commandName());

        if (!(controlCommand instanceof Setup)) {
            return new CommandResponse.Error(runId, "Only Setup supported");
        }

        switch (controlCommand.commandName().name()) {
            case "colorStep" -> {
                workerActor.tell(new ExecuteColorStep(runId, controlCommand, resultsStore));
                return new CommandResponse.Started(runId);
            }
            case "ttOffsetsToActs" -> {
                workerActor.tell(new ExecuteTtOffsetsToActs(runId, controlCommand, resultsStore));
                return new CommandResponse.Started(runId);
            }
            case "decomposeActs" -> {
                workerActor.tell(new ExecuteDecomposeActs(runId, controlCommand, resultsStore));
                return new CommandResponse.Started(runId);
            }
            default -> {
                return new CommandResponse.Error(runId, "Unsupported command");
            }
        }
    }
}
