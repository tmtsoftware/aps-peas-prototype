package peas.computationassembly;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class Results {

    private static final Results INSTANCE = new Results();
    private final Map<String, Object> data = new ConcurrentHashMap<>();

    public Results() {
        // in out test, we have these pre-initialized from a 'previous step'
        float[] centroidOffsetsX = {-0.172f, -0.783f, -0.736f, 1.541f, 0.937f, 0.688f, -0.573f, -1.473f, -1.925f, -1.406f,
                -1.861f, -0.151f, 0.814f, 0.688f, 2.336f, 2.152f, 2.830f, 1.567f, -0.722f, -2.009f, -2.523f, -3.187f,
                -2.840f, -2.990f, -3.044f, -1.716f, 5.332f, -5.395f, 1.456f, 1.453f, 2.334f, 2.370f, 1.381f, 2.058f, 2.213f, 1.357f};

        float[] centroidOffsetsY = {0.770f, 0.790f, -0.227f, -0.840f, -0.596f, -0.002f, 2.184f, 1.379f, 1.673f, 0.891f, -1.017f,
                -1.913f, -2.428f, -1.720f, -1.569f, -0.760f, -1.677f, 1.239f, 3.146f, 3.277f, 2.767f, 2.677f, 1.470f, -0.154f,
                -1.825f, -2.638f, 3.006f, -9.161f, -2.118f, -2.004f, 0.162f, -2.130f, -0.984f, 3.866f, 1.741f, 2.723f};

        data.put("centroidOffsets.centroidOffsetsX", centroidOffsetsX);
        data.put("centroidOffsets.centroidOffsetsY", centroidOffsetsY);
    }
    public static Results getInstance() {
        return INSTANCE;
    }

    public void set(String name, Object value) {
        data.put(name, value);
    }

    public void dump() {

    }

    @SuppressWarnings("unchecked")
    public <T> T get(String name) {
        return (T) data.get(name);
    }

    public boolean contains(String name) {
        return data.containsKey(name);
    }

    public void printAll() {

        if (data == null || data.isEmpty()) {
            System.out.println("Results is empty");
            return;
        }

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            System.out.print(entry.getKey() + " = ");
            printValue(entry.getValue());
        }
    }
    private void printValue(Object value) {

        if (value == null) {
            System.out.println("null");
            return;
        }

        printArrayRecursive(value, 0);
        System.out.println();
    }

    private void printArrayRecursive(Object value, int indentLevel) {

        Class<?> clazz = value.getClass();

        if (!clazz.isArray()) {
            System.out.print(value);
            return;
        }

        int length = java.lang.reflect.Array.getLength(value);

        System.out.print("[");
        for (int i = 0; i < length; i++) {

            Object element = java.lang.reflect.Array.get(value, i);

            if (element != null && element.getClass().isArray()) {
                System.out.println();
                printIndent(indentLevel + 1);
                printArrayRecursive(element, indentLevel + 1);
            } else {
                System.out.print(element);
            }

            if (i < length - 1) {
                System.out.print(", ");
            }
        }
        System.out.print("]");
    }

    private void printIndent(int level) {
        for (int i = 0; i < level; i++) {
            System.out.print("  ");
        }
    }    public void clear() {
        data.clear();
    }

}
