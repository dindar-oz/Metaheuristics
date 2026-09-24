package base;

import base.terminalcondition.AndCompoundTC;
import base.terminalcondition.OrCompoundTC;
import metaheuristic.MetaHeuristic;

import java.util.Arrays;

/**
 * Decides when a metaheuristic run stops. init() is called at the start of every run.
 */
@FunctionalInterface
public interface TerminalCondition {

    boolean isSatisfied(MetaHeuristic alg);

    default void init() {
    }

    static TerminalCondition never() {
        return alg -> false;
    }

    static TerminalCondition anyOf(TerminalCondition... conditions) {
        return new OrCompoundTC(Arrays.asList(conditions));
    }

    static TerminalCondition allOf(TerminalCondition... conditions) {
        return new AndCompoundTC(Arrays.asList(conditions));
    }
}
