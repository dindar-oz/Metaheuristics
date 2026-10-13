package lab.model;

import metaheuristic.run.RunHandle;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.TerminalCondition;

import javax.swing.*;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * A problem as the lab sees it: named parameters, the algorithms that can solve it and a way to run them.
 * The solution types stay hidden behind this interface.
 */
public interface LabProblem {

    String name();

    List<ParamSpec> params();

    List<String> algorithmNames();

    List<ParamSpec> algorithmParams(String algorithm);

    // Creates the problem instance(s) for the given parameter values
    Session newSession(ParamValues problemParams);

    /**
     * One problem instance, ready to run algorithms on.
     */
    interface Session {
        String describe();

        // Validates the parameters (throws IllegalArgumentException) and starts the run on the executor
        StartedRun start(String algorithm, ParamValues values, long seed, TerminalCondition stop,
                         SearchListener listener, Executor executor);
    }

    /**
     * A started run. Keeps the run and its typed problem together, so that its best solution can be shown with the
     * problem's SolutionVisualizer.
     */
    interface StartedRun {
        RunHandle<?> handle();

        // true once the run has evaluated at least one solution
        boolean hasSolution();

        // a view of the final (or, while running, the best so far) solution; call on the Swing thread
        JComponent viewBestSolution();
    }
}
