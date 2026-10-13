package lab.model;

import metaheuristic.run.SearchListener;
import metaheuristic.termination.TerminalCondition;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * One problem offered through several encodings (e.g. the wind farm layout as a real vector for DE/PSO and as a set
 * of cells for EA/SA). All parts share the same parameters and objective, so their runs can be compared in one
 * session; each algorithm runs on the encoding of the part that offers it.
 */
public final class MultiEncodingProblem implements LabProblem {

    private final String name;
    private final List<ParamSpec> params;
    private final List<ProblemSpec<?, ?>> parts;
    private final Map<String, ProblemSpec<?, ?>> partOf = new LinkedHashMap<>();

    // Every part must take the given parameters; algorithm names must be unique across parts
    public MultiEncodingProblem(String name, List<ParamSpec> params, List<ProblemSpec<?, ?>> parts) {
        this.name = name;
        this.params = params;
        this.parts = new ArrayList<>(parts);
        for (ProblemSpec<?, ?> part : parts)
            for (String algorithm : part.algorithmNames())
                if (partOf.put(algorithm, part) != null)
                    throw new IllegalArgumentException("algorithm " + algorithm + " is offered by two encodings");
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public List<ParamSpec> params() {
        return params;
    }

    @Override
    public List<String> algorithmNames() {
        return new ArrayList<>(partOf.keySet());
    }

    @Override
    public List<ParamSpec> algorithmParams(String algorithm) {
        return part(algorithm).algorithmParams(algorithm);
    }

    // The encodings' problem instances are created when an algorithm of that encoding is first started
    @Override
    public Session newSession(ParamValues problemParams) {
        ParamValues values = problemParams.copy();
        Map<ProblemSpec<?, ?>, Session> sessions = new HashMap<>();
        String description = name + " (" + values + ")";
        return new Session() {
            @Override
            public String describe() {
                return description;
            }

            @Override
            public synchronized StartedRun start(String algorithm, ParamValues algorithmValues, long seed, TerminalCondition stop,
                                                 SearchListener listener, Executor executor) {
                Session session = sessions.computeIfAbsent(part(algorithm), part -> part.newSession(values));
                return session.start(algorithm, algorithmValues, seed, stop, listener, executor);
            }
        };
    }

    private ProblemSpec<?, ?> part(String algorithm) {
        ProblemSpec<?, ?> part = partOf.get(algorithm);
        if (part == null)
            throw new IllegalArgumentException("Unknown algorithm " + algorithm);
        return part;
    }

    @Override
    public String toString() {
        return name;
    }
}
