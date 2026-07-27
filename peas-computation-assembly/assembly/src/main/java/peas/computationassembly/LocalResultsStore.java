package peas.computationassembly;

/**
 * In-process implementation of ResultsStore.
 * Delegates directly to the existing Results singleton — zero behaviour change.
 */
public class LocalResultsStore implements ResultsStore {

    private final Results results = Results.getInstance();

    @Override
    public Object get(String key) {
        return results.get(key);
    }

    @Override
    public void set(String key, Object value) {
        results.set(key, value);
    }
}
