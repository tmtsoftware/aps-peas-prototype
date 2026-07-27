package peas.computationassembly;

import com.typesafe.config.*;
import java.io.File;
import java.util.*;

/**
 * Generalized APS HOCON reader.
 *
 * Discovers column names and types automatically from the data, and stores
 * each column in a HashMap keyed by "<filename>.<columnName>".
 *
 * Type inference rules (applied to each field in the first row):
 *   - ConfigList                    -> float[][]
 *   - NUMBER with no decimal point  -> int[]
 *   - NUMBER with decimal point     -> float[]
 *   - STRING parseable as long      -> int[]
 *   - STRING parseable as float     -> float[]
 *   - Anything else                 -> String[]
 *
 * Three entry points are provided:
 *   readFile(String path, String filename, String arrayKey)  -- from file, explicit array key
 *   readFile(String path, String filename)                   -- from file, auto-detect array key
 *   readFile(Config cfg,  String filename)                   -- from pre-parsed Config (e.g. from CSW)
 *
 * All three funnel into a single private loadFromConfig() method.
 *
 * Dependency:
 *   com.typesafe:config:1.4.3
 *
 * Maven:
 *   <dependency>
 *     <groupId>com.typesafe</groupId>
 *     <artifactId>config</artifactId>
 *     <version>1.4.3</version>
 *   </dependency>
 *
 * Gradle:
 *   implementation 'com.typesafe:config:1.4.3'
 */
public class HoconReader {

    // ── Column type enum ─────────────────────────────────────────────────────
    private enum ColType { INT, FLOAT, STRING, FLOAT2D }

    // ── Central store: "<filename>.<columnName>" -> typed array ─────────────
    private Constants constants = Constants.getInstance();

    // ── Helper: build store key ──────────────────────────────────────────────
    private String key(String filename, String column) {
        return filename + "." + column;
    }

    // =========================================================================
    // Type inference from a single ConfigValue
    // =========================================================================
    private ColType inferType(ConfigValue value) {
        switch (value.valueType()) {
            case LIST:
                return ColType.FLOAT2D;
            case NUMBER: {
                // Use the rendered string to detect whether a decimal point exists
                String rendered = value.render();
                return rendered.contains(".") ? ColType.FLOAT : ColType.INT;
            }
            case STRING: {
                // Quoted values in HOCON (e.g. rings: "6" or mode: "PT")
                String s = (String) value.unwrapped();
                try {
                    Long.parseLong(s);
                    return ColType.INT;
                } catch (NumberFormatException e1) {
                    try {
                        Float.parseFloat(s);
                        return ColType.FLOAT;
                    } catch (NumberFormatException e2) {
                        return ColType.STRING;
                    }
                }
            }
            default:
                return ColType.STRING;
        }
    }

    // =========================================================================
    // Array key auto-detection — shared by all entry points that need it
    // =========================================================================
    private String detectArrayKey(Config cfg, String context) {
        for (Map.Entry<String, ConfigValue> entry : cfg.entrySet()) {
            if (entry.getValue().valueType() == ConfigValueType.LIST) {
                return entry.getKey();
            }
        }
        throw new IllegalArgumentException("No top-level list key found in: " + context);
    }

    // =========================================================================
    // Core logic — all entry points funnel here
    // =========================================================================
    private void loadFromConfig(Config cfg, String filename, String arrayKey) {
        List<? extends ConfigObject> rows = cfg.getObjectList(arrayKey);

        if (rows.isEmpty()) {
            System.out.println("Warning: no rows found for " + filename);
            return;
        }

        int n = rows.size();

        // ── Step 1: discover columns and infer types from first row ──────────
        ConfigObject firstRow = rows.get(0);
        // LinkedHashMap preserves column declaration order
        Map<String, ColType> colTypes = new LinkedHashMap<>();
        for (String col : firstRow.keySet()) {
            colTypes.put(col, inferType(firstRow.get(col)));
        }

        // ── Step 2: allocate arrays ──────────────────────────────────────────
        Map<String, Object> arrays = new LinkedHashMap<>();
        for (Map.Entry<String, ColType> e : colTypes.entrySet()) {
            switch (e.getValue()) {
                case INT:    arrays.put(e.getKey(), new int[n]);    break;
                case FLOAT:  arrays.put(e.getKey(), new float[n]);  break;
                case STRING: arrays.put(e.getKey(), new String[n]); break;
                case FLOAT2D: arrays.put(e.getKey(), new float[n][]); break;
            }
        }

        // ── Step 3: populate arrays row by row ──────────────────────────────
        for (int i = 0; i < n; i++) {
            Config r = rows.get(i).toConfig();
            for (Map.Entry<String, ColType> e : colTypes.entrySet()) {
                String col = e.getKey();
                switch (e.getValue()) {
                    case INT:
                        ((int[]) arrays.get(col))[i] = r.getInt(col);
                        break;
                    case FLOAT:
                        ((float[]) arrays.get(col))[i] = (float) r.getDouble(col);
                        break;
                    case STRING:
                        ((String[]) arrays.get(col))[i] = r.getString(col);
                        break;
                    case FLOAT2D: {
                        List<Double> vals = r.getDoubleList(col);
                        float[] arr = new float[vals.size()];
                        for (int j = 0; j < vals.size(); j++) arr[j] = vals.get(j).floatValue();
                        ((float[][]) arrays.get(col))[i] = arr;
                        break;
                    }
                }
            }
        }

        // ── Step 4: store every column ───────────────────────────────────────
        for (String col : colTypes.keySet()) {
            constants.put(key(filename, col), arrays.get(col));
        }
    }

    // =========================================================================
    // Public entry point 1: from a file path + explicit array key
    // =========================================================================
    /**
     * Reads a HOCON file from disk given its path, filename stem, and the
     * name of the top-level list key (e.g. "lenslets", "segments").
     */
    public void readFile(String path, String filename, String arrayKey) {
        loadFromConfig(ConfigFactory.parseFile(new File(path)), filename, arrayKey);
    }

    // =========================================================================
    // Public entry point 2: from a file path, auto-detect array key
    // =========================================================================
    /**
     * Reads a HOCON file from disk, scanning for the first top-level list key
     * automatically so the caller does not need to know it.
     */
    public void readFile(String path, String filename) {
        Config cfg = ConfigFactory.parseFile(new File(path));
        loadFromConfig(cfg, filename, detectArrayKey(cfg, path));
    }

    // =========================================================================
    // Public entry point 3: from a pre-parsed Config (e.g. from CSW Config Service)
    // =========================================================================
    /**
     * Reads from an already-parsed com.typesafe.config.Config object — used
     * when the config data was fetched from the CSW Configuration Service
     * rather than loaded from a local file.
     */
    public void readFile(Config cfg, String filename) {
        loadFromConfig(cfg, filename, detectArrayKey(cfg, filename));
    }



    // =========================================================================
    // Typed accessors — avoid unchecked casts at call sites
    // =========================================================================
    public int[]     getIntColumn     (String filename, String col) { return (int[])     constants.get(key(filename, col)); }
    public float[]   getFloatColumn   (String filename, String col) { return (float[])   constants.get(key(filename, col)); }
    public String[]  getStringColumn  (String filename, String col) { return (String[])  constants.get(key(filename, col)); }
    public float[][] getFloat2DColumn (String filename, String col) { return (float[][]) constants.get(key(filename, col)); }


}