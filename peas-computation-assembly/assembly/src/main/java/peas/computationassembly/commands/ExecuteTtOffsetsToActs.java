package peas.computationassembly.commands;

import peas.computationassembly.AlgorithmLibrary;
import peas.computationassembly.ResultsStore;
import peas.computationassembly.metadata.ComputationParameter;
import csw.params.commands.ControlCommand;
import csw.params.commands.Result;
import csw.params.commands.Setup;
import csw.params.core.models.Id;

import java.util.List;

public class ExecuteTtOffsetsToActs extends AbstractExecuteCommand {

    private static final List<ComputationParameter> METADATA = List.of(
        new ComputationParameter("actuatorPositionsX", float.class, new int[]{108}, ComputationParameter.Source.CONSTANT,      ComputationParameter.Direction.INPUT),
        new ComputationParameter("actuatorPositionsY", float.class, new int[]{108}, ComputationParameter.Source.CONSTANT,      ComputationParameter.Direction.INPUT),
        new ComputationParameter("secPerPix",          float.class, new int[]{},    ComputationParameter.Source.CONSTANT,      ComputationParameter.Direction.INPUT),
        new ComputationParameter("centroidOffsetsX",   float.class, new int[]{36},  ComputationParameter.Source.RESULTS,       ComputationParameter.Direction.INPUT),
        new ComputationParameter("centroidOffsetsY",   float.class, new int[]{36},  ComputationParameter.Source.RESULTS,       ComputationParameter.Direction.INPUT),
        new ComputationParameter("mirrorConfig",       int.class,   new int[]{36},  ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
        new ComputationParameter("xOffsetsOut",        float.class, new int[]{36},  ComputationParameter.Destination.RESULTS,  ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("yOffsetsOut",        float.class, new int[]{36},  ComputationParameter.Destination.RESULTS,  ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("desiredActDeltas",   float.class, new int[]{36,3},ComputationParameter.Destination.RESULTS,  ComputationParameter.Direction.OUTPUT)
    );

    public ExecuteTtOffsetsToActs(Id runId, ControlCommand controlCommand, ResultsStore resultsStore) {
        super(runId, (Setup) controlCommand, resultsStore);
    }

    @Override
    protected List<ComputationParameter> getMetadata() {
        return METADATA;
    }

    @Override
    protected Result callAlgorithm(AlgorithmLibrary library, Object[] args) throws Exception {

        library.ttOffsetsToActs(
                (float[])   args[0],  // actuatorPositionsX
                (float[])   args[1],  // actuatorPositionsY
                (float)     args[2],  // secPerPix
                (float[])   args[3],  // centroidOffsetsX
                (float[])   args[4],  // centroidOffsetsY
                (int[])     args[5],  // mirrorConfig
                (float[])   args[6],  // xOffsetsOut
                (float[])   args[7],  // yOffsetsOut
                (float[][]) args[8]   // desiredActDeltas
        );
        return new Result();
    }
}
