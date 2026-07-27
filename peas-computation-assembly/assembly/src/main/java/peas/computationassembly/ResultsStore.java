package peas.computationassembly;

/**
 * Strategy interface for storing and retrieving intermediate computation results.
 *
 * Two implementations:
 *   LocalResultsStore  - in-process cache (wraps existing Results singleton)
 *   RemoteResultsStore - delegates to the APS Procedure Data Service via HTTP
 *
 * The active implementation is injected into AbstractExecuteCommand.
 * Execute* classes and their metadata are completely unaffected by the choice.
 */
public interface ResultsStore {

    /**
     * Retrieve a previously stored value by key.
     * Key convention: "{commandName}.{paramName}"
     * e.g. "ttOffsetsToActs.desiredActDeltas"
     */
    Object get(String key);

    /**
     * Store a value under the given key.
     */
    void set(String key, Object value);
}
