package metaheuristic.termination;

import metaheuristic.run.Progress;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Satisfied when all of the conditions are satisfied. */
public final class AndCompoundTC implements TerminalCondition {
    private final List<TerminalCondition> conditions;

    public AndCompoundTC(List<TerminalCondition> conditions) {
        this.conditions = Collections.unmodifiableList(new ArrayList<>(conditions));
    }

    @Override
    public boolean isSatisfied(Progress p) {
        return conditions.stream().allMatch(c -> c.isSatisfied(p));
    }
}
