package peas.computationassembly.metadata;

public class ComputationParameter {

    public enum Source { CONFIGURATION, COMMAND, CONSTANT, RESULTS }
    public enum Destination { RESPONSE, RESULTS, BOTH }
    public enum Direction { INPUT, OUTPUT }

    public final String name;          // formal Fortran name
    public final Class<?> type;        // int, float, float[], etc.
    public int[] dimensions;     // scalar = {}, 1D = {n}, 2D = {n,m}
    public final Source source;        // where to read input from
    public final Destination destination;        // where to read input from
    public final Direction direction;  // INPUT or OUTPUT

    public ComputationParameter (
            String name,
            Class<?> type,
            int[] dimensions,
            Source source,
            Destination destination,
            Direction direction
    ) {
        this.name = name;
        this.type = type;
        this.dimensions = dimensions;
        this.source = source;
        this.destination = destination;
        this.direction = direction;
    }
    public ComputationParameter (
            String name,
            Class<?> type,
            int[] dimensions,
            Source source,
            Direction direction
    ) {
        this.name = name;
        this.type = type;
        this.dimensions = dimensions;
        this.source = source;
        this.destination = null;
        this.direction = direction;
    }

    public ComputationParameter (
            String name,
            Class<?> type,
            int[] dimensions,

            Destination destination,
            Direction direction
    ) {
        this.name = name;
        this.type = type;
        this.dimensions = dimensions;
        this.source = null;
        this.destination = destination;
        this.direction = direction;
    }


}
