package peas.computationassembly.commands;

import peas.computationassembly.AlgorithmLibrary;
import peas.computationassembly.Configuration;
import peas.computationassembly.Constants;
import peas.computationassembly.RemoteResultsStore;
import peas.computationassembly.ResultsStore;
import peas.computationassembly.metadata.ComputationParameter;
import peas.computationassembly.metadata.ComputationUtils;
import csw.params.commands.Result;
import csw.params.commands.Setup;
import csw.params.core.generics.Key;
import csw.params.core.models.Id;
import csw.params.javadsl.JKeyType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Base class for all Execute* command classes.
 *
 * Execution lifecycle:
 *   1. prefetch  - if RemoteResultsStore, fetches all Source.RESULTS inputs in one HTTP call
 *   2. resolve   - iterates metadata, populating args[] from Setup, ResultsStore,
 *                  Configuration, or Constants as declared per parameter
 *   3. execute   - calls the algorithm via callAlgorithm()
 *   4. store     - writes all OUTPUT parameters to the ResultsStore
 *   5. flush     - if RemoteResultsStore, posts all Destination.RESULTS outputs in one HTTP call
 *
 * Switching between LocalResultsStore and RemoteResultsStore requires no changes
 * to subclasses or their metadata.
 */
public abstract class AbstractExecuteCommand implements WorkerCommand {

    protected final Id runId;
    protected final Setup setup;
    private final ResultsStore resultsStore;

    protected AbstractExecuteCommand(Id runId, Setup setup, ResultsStore resultsStore) {
        this.runId = runId;
        this.setup = setup;
        this.resultsStore = resultsStore;
    }

    @Override
    public Id runId() {
        return runId;
    }

    /** Subclasses declare their ordered ComputationParameter list here. */
    protected abstract List<ComputationParameter> getMetadata();

    /**
     * Subclasses implement the actual algorithm call here.
     * The args array is in the exact same order as getMetadata().
     */
    protected abstract Result callAlgorithm(AlgorithmLibrary library, Object[] args) throws Exception;

    /**
     * Derives the command name used as the key prefix for results storage.
     * e.g. ExecuteColorStep -> "colorStep"
     */
    protected String commandKey() {
        String simpleName = getClass().getSimpleName();
        if (simpleName.startsWith("Execute")) {
            String stripped = simpleName.substring("Execute".length());
            return Character.toLowerCase(stripped.charAt(0)) + stripped.substring(1);
        }
        return simpleName;
    }

    @Override
    public final Result execute(AlgorithmLibrary library) throws Exception {

        List<ComputationParameter> metadata = getMetadata();
        Configuration config = Configuration.getInstance();
        Constants constants = Constants.getInstance();
        String cmdKey = commandKey();

        // ── 1. Prefetch all Source.RESULTS inputs in one call (remote only) ──
// ── 1. Prefetch all Source.RESULTS inputs in one call (remote only) ──
        if (resultsStore instanceof RemoteResultsStore remote) {
            Map<String, String> referenceOverrides = new HashMap<>();
            for (ComputationParameter p : metadata) {
                Optional<?> setupValue = extractFromSetup(p);
                if (setupValue.isPresent() && setupValue.get() instanceof String referenceKey
                        && referenceKey.contains(".")) {
                    referenceOverrides.put(p.name, referenceKey);
                }
            }
            remote.prefetch(cmdKey, metadata, referenceOverrides);
        }
        Object[] args = new Object[metadata.size()];

        // ── 2. Resolve all INPUT parameters ──────────────────────────────────
        for (int i = 0; i < metadata.size(); i++) {
            ComputationParameter p = metadata.get(i);

            if (p.direction == ComputationParameter.Direction.OUTPUT) {
                args[i] = ComputationUtils.allocateArray(p.type, p.dimensions);
                continue;
            }

            // Setup command always takes priority over any other source
            Optional<?> setupValue = extractFromSetup(p);
            if (setupValue.isPresent()) {
                Object overrideValue = setupValue.get();
                if (overrideValue instanceof String referenceKey) {
                    // String value in the command means "look up this key in the results store"
                    args[i] = resultsStore.get(referenceKey);
                } else {
                    args[i] = overrideValue;
                }
                continue;
            }

            switch (p.source) {
                case RESULTS       -> args[i] = resultsStore.get(cmdKey + "." + p.name);
                case CONFIGURATION -> args[i] = config.get(p.name);
                case CONSTANT      -> args[i] = constants.get(p.name);
                default -> throw new IllegalStateException(
                        "Unhandled source [" + p.source + "] for parameter [" + p.name + "]");
            }
        }

        // ── 3. Execute the algorithm ──────────────────────────────────────────
        Result result = callAlgorithm(library, args);

        // ── 4. Write all OUTPUT parameters to the results store ───────────────
        for (int i = 0; i < metadata.size(); i++) {
            ComputationParameter p = metadata.get(i);
            if (p.direction != ComputationParameter.Direction.OUTPUT) continue;

            if (p.destination == ComputationParameter.Destination.RESULTS) {
                resultsStore.set(cmdKey + "." + p.name, args[i]);
            } else {
                throw new IllegalStateException(
                        "Unhandled destination [" + p.destination + "] for parameter [" + p.name + "]");
            }
        }

        // ── 5. Flush all Destination.RESULTS outputs in one call (remote only) 
        if (resultsStore instanceof RemoteResultsStore remote) {
            remote.flush(cmdKey, metadata);
        }

        return result;
    }

    private Optional<?> extractFromSetup(ComputationParameter p) {
        try {
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
            // String fallback — used for reference keys pointing into the results store
            Key<String> stringKey = JKeyType.StringKey().make(p.name);
            Optional<?> stringResult = setup.jGet(stringKey).map(param -> param.head());
            if (stringResult.isPresent()) return stringResult;
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }
}
