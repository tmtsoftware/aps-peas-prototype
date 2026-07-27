package peas.computationassembly.metadata;

public class ComputationUtils {

    public static Object allocateArray(Class<?> type, int[] dimensions) {

        // Scalar case
        if (dimensions == null || dimensions.length == 0) {
            if (type == int.class || type == Integer.class) return 0;
            if (type == float.class || type == Float.class) return 0.0f;
            if (type == double.class || type == Double.class) return 0.0;
            if (type == long.class || type == Long.class) return 0L;
            if (type == boolean.class || type == Boolean.class) return false;

            throw new IllegalArgumentException("Unsupported scalar type: " + type);
        }

        // Array case (1D, 2D, 3D, ...)
        return java.lang.reflect.Array.newInstance(type, dimensions);
    }

}
