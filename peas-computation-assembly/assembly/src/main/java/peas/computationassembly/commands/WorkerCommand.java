package peas.computationassembly.commands;

import csw.params.commands.Result;
import csw.params.core.models.Id;
import peas.computationassembly.AlgorithmLibrary;

public interface WorkerCommand {

    Id runId();

    Result execute(AlgorithmLibrary library) throws Exception;
}
