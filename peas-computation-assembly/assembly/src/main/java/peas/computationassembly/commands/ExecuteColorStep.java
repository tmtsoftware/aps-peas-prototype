package peas.computationassembly.commands;

import peas.computationassembly.AlgorithmLibrary;
import peas.computationassembly.ResultsStore;
import peas.computationassembly.metadata.ComputationParameter;
import csw.params.commands.ControlCommand;
import csw.params.commands.Result;
import csw.params.commands.Setup;
import csw.params.core.models.Id;
import csw.params.javadsl.JKeyType;

import java.util.List;

public class ExecuteColorStep extends AbstractExecuteCommand {

    // Metadata is non-static because output dimensions depend on stepCount,
    // which is resolved at construction time from the Setup command.
    private final List<ComputationParameter> metadata;

    public ExecuteColorStep(Id runId, ControlCommand controlCommand, ResultsStore resultsStore) {
        super(runId, (Setup) controlCommand, resultsStore);

        Setup setup = (Setup) controlCommand;
        int stepCount = setup.jGet(JKeyType.IntKey().make("stepCount"))
                .map(p -> p.head())
                .orElseThrow(() -> new IllegalArgumentException("stepCount missing from Setup command"));

        metadata = List.of(
            new ComputationParameter("stepCount",  int.class,   new int[]{},              ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
            new ComputationParameter("stepSizeNm", float.class, new int[]{},              ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
            new ComputationParameter("colorSteps", float.class, new int[]{stepCount+1,3}, ComputationParameter.Destination.RESULTS,  ComputationParameter.Direction.OUTPUT)
        );
    }

    @Override
    protected List<ComputationParameter> getMetadata() {
        return metadata;
    }

    @Override
    protected Result callAlgorithm(AlgorithmLibrary library, Object[] args) throws Exception {
        library.colorStep(
                (int)      args[0],  // stepCount
                (float)    args[1],  // stepSizeNm
                (float[][])args[2]   // colorSteps (output, pre-allocated)
        );
        return new Result();
    }
}
