
package peas.computationassembly.commands;
import peas.computationassembly.Constants;
import csw.params.commands.ControlCommand;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import peas.computationassembly.metadata.ComputationParameter;
import csw.params.commands.*;
import csw.params.core.models.*;
import peas.computationassembly.AlgorithmLibrary;
import peas.computationassembly.Configuration;
import peas.computationassembly.Results;
import peas.computationassembly.metadata.ComputationUtils;
import csw.params.javadsl.JKeyType;

import java.util.List;
import java.util.Optional;


public class ExecuteCalculatePupilRegError implements WorkerCommand {

    private final Id runId;
    private Setup setup;

    int numSpots = 508; // will be gereralized for differnet cmd calls

    private final List<ComputationParameter> metadata = List.of(
        new ComputationParameter("fractionalIntensityCalcMethod", float.class, new int[]{}, ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
        new ComputationParameter("intensities", float.class, new int[]{numSpots}, ComputationParameter.Source.RESULTS, ComputationParameter.Direction.INPUT),
        new ComputationParameter("numSpots", int.class, new int[]{}, ComputationParameter.Source.COMMAND, ComputationParameter.Direction.INPUT),
        new ComputationParameter("peripheralSpotPerp", float.class, new int[]{36}, ComputationParameter.Source.CONSTANT, ComputationParameter.Direction.INPUT),
        new ComputationParameter("peripheralSpotParallel", float.class, new int[]{36}, ComputationParameter.Source.CONSTANT, ComputationParameter.Direction.INPUT),
        new ComputationParameter("peripheralSpotTheta", float.class, new int[]{36}, ComputationParameter.Source.CONSTANT, ComputationParameter.Direction.INPUT),
        new ComputationParameter("aHex", float.class, new int[]{}, ComputationParameter.Source.CONSTANT, ComputationParameter.Direction.INPUT),
        new ComputationParameter("spotDiameter", float.class, new int[]{}, ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
        new ComputationParameter("nspotTypes", int.class, new int[]{numSpots}, ComputationParameter.Source.CONSTANT, ComputationParameter.Direction.INPUT),
        new ComputationParameter("missingSpotFlags", int.class, new int[]{numSpots}, ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
        new ComputationParameter("findCentStatusList", int.class, new int[]{numSpots}, ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
        new ComputationParameter("pupilRegistrationOffsetX", float.class, new int[]{numSpots}, ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),
        new ComputationParameter("pupilRegistrationOffsetY", float.class, new int[]{numSpots}, ComputationParameter.Source.CONFIGURATION, ComputationParameter.Direction.INPUT),

        new ComputationParameter("regErrorX", float.class, new int[]{}, ComputationParameter.Destination.RESPONSE, ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("regErrorY", float.class, new int[]{}, ComputationParameter.Destination.RESPONSE, ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("regErrorPhi", float.class, new int[]{}, ComputationParameter.Destination.RESPONSE, ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("regErrorApproxX", float.class, new int[]{}, ComputationParameter.Destination.RESPONSE, ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("regErrorApproxY", float.class, new int[]{}, ComputationParameter.Destination.RESPONSE, ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("regErrorApproxPhi", float.class, new int[]{}, ComputationParameter.Destination.RESPONSE, ComputationParameter.Direction.OUTPUT),
        new ComputationParameter("regScaleError", float.class, new int[]{}, ComputationParameter.Destination.RESPONSE, ComputationParameter.Direction.OUTPUT)
    );

    public ExecuteCalculatePupilRegError(Id runId, ControlCommand controlCommand) {
        this.runId = runId;
        setup = (Setup) controlCommand;
    }

    @Override
    public Id runId() {
        return runId;
    }

    @Override
    public Result execute(AlgorithmLibrary library) throws Exception {

        Results results = Results.getInstance();
        Configuration config = Configuration.getInstance();
        Constants constants = Constants.getInstance();

        // Prepare argument array in exact metadata order
        Object[] argsForFortran = new Object[metadata.size()];

        for (int i = 0; i < metadata.size(); i++) {
            ComputationParameter p = metadata.get(i);
            if (p.direction == ComputationParameter.Direction.INPUT) {
                // Read input from the appropriate source
                // Add logic to see if argument name is present in Setup command, and if so, override argsForFortran value with the one from the command
                Object value = null;

                // Try to override with Setup value if present
                Optional<?> setupValue = extractFromSetup(p);

                if (setupValue.isPresent()) {
                    // we have a setup override, either use results name or actual value
                    Object overrideValue = setupValue.get();

                    if (overrideValue instanceof String) {
                        // use reference value from cmd
                        String referenceKey = (String) overrideValue;
                        value = results.get(referenceKey);
                    } else {
                        // use value directly from command
                        value = setupValue.get();
                    }

                } else if (p.source == ComputationParameter.Source.CONFIGURATION) {
                    value = config.get(p.name);
                } else if (p.source == ComputationParameter.Source.CONSTANT){
                    value = constants.get(p.name);
                }
                argsForFortran[i] = value;

            } else {
                // Allocate output container of the correct type and shape
                argsForFortran[i] = ComputationUtils.allocateArray(p.type, p.dimensions);
            }
        }

        library.decomposeActs((float[][])argsForFortran[0], (float[])argsForFortran[1], (float[])argsForFortran[2]);

        // populate results data cache
        for (int i = 0; i < metadata.size(); i++) {
            ComputationParameter p = metadata.get(i);
            if (p.direction == ComputationParameter.Direction.OUTPUT) {
                if (p.destination == ComputationParameter.Destination.RESULTS) {
                    String key = setup.commandName().name() + "." + p.name;
                    results.set(key, argsForFortran[i]);
                } else if (p.destination == ComputationParameter.Destination.RESPONSE) {
                    // TODO: return as RESPONSE variables

                } else {
                    throw new Exception("output destination not RESULTS or RESPONSE");
                }
            }
        }

        // testing/debugging
        Thread.sleep(1000);
        results.printAll();

        return new Result();
    }

    // -----------------------------------------
    // Helper: Extract parameter from Setup
    // -----------------------------------------
    private Optional<?> extractFromSetup(ComputationParameter p) {

        try {
            // Metadata-driven types
            if (p.type == int.class) {
                Key<Integer> key = JKeyType.IntKey().make(p.name);
                Optional<?> result = setup.jGet(key).map(param -> param.head());
                if (result.isPresent()) return result;
            }

            if (p.type == float.class) {
                Key<Float> key = JKeyType.FloatKey().make(p.name);
                Optional<?> result = setup.jGet(key).map(param -> param.head());
                if (result.isPresent()) return result;
            }

            // 🔹 NEW: fallback to String if present in command
            Key<String> stringKey = JKeyType.StringKey().make(p.name);
            Optional<?> stringResult = setup.jGet(stringKey).map(param -> param.head());
            if (stringResult.isPresent()) return stringResult;

        } catch (Exception ignored) {
        }

        return Optional.empty();
    }

}









