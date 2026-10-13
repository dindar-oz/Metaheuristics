package metaheuristic.termination;

import metaheuristic.run.Progress;

import java.util.Arrays;

/**
 * Decides when a run stops. Implementations must be stateless functions of the Progress,
 * so that one instance can be shared by any number of (concurrent) runs.
 */
@FunctionalInterface
public interface TerminalCondition {

    boolean isSatisfied(Progress progress);

    static TerminalCondition never() {
        return p -> false;
    }

    static TerminalCondition anyOf(TerminalCondition... conditions) {
        return new OrCompoundTC(Arrays.asList(conditions));
    }

    static TerminalCondition allOf(TerminalCondition... conditions) {
        return new AndCompoundTC(Arrays.asList(conditions));
    }

    default TerminalCondition or(TerminalCondition other) {
        return anyOf(this, other);
    }
}
