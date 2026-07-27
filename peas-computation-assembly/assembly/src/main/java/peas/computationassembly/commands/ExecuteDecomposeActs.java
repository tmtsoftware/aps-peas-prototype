package peas.computationassembly.commands;

import peas.computationassembly.AlgorithmLibrary;
import peas.computationassembly.ResultsStore;
import peas.computationassembly.metadata.ComputationParameter;
import csw.params.commands.ControlCommand;
import csw.params.commands.Result;
import csw.params.commands.Setup;
import csw.params.core.models.Id;

import java.util.List;

public class ExecuteDecomposeActs extends AbstractExecuteCommand {

    private static final List<ComputationParameter> METADATA = List.of(
        new ComputationParameter("desiredActDeltas", float.class, new int[]{36,3}, ComputationParameter.Source.RESULTS,     ComputationParameter.Direction.INPUT),
        new ComputationParameter("tipTiltActs",      float.class, new int[]{108},  ComputationParameter.Destination.RESULTS, ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("pistonActs",       float.class, new int[]{108},  ComputationParameter.Destination.RESULTS, ComputationParameter.Direction.OUTPUT)
    );

    public ExecuteDecomposeActs(Id runId, ControlCommand controlCommand, ResultsStore resultsStore) {
        super(runId, (Setup) controlCommand, resultsStore);
    }

    @Override
    protected List<ComputationParameter> getMetadata() {
        return METADATA;
    }

    @Override
    protected Result callAlgorithm(AlgorithmLibrary library, Object[] args) throws Exception {
        library.decomposeActs(
                (float[][]) args[0],  // desiredActDeltas
                (float[])   args[1],  // tipTiltActs
                (float[])   args[2]   // pistonActs
        );
        return new Result();
    }
}
