package metaheuristic.termination;

import metaheuristic.run.Progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Satisfied when any of the conditions is satisfied. */
public final class OrCompoundTC implements TerminalCondition {
    private final List<TerminalCondition> conditions;

    public OrCompoundTC(List<TerminalCondition> conditions) {
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
    }

    @Override
    public boolean isSatisfied(Progress p) {
        return conditions.stream().anyMatch(c -> c.isSatisfied(p));
    }
}
