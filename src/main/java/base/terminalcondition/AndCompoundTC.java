package base.terminalcondition;

import base.TerminalCondition;
import metaheuristic.MetaHeuristic;

import java.util.ArrayList;
import java.util.List;

/** Satisfied when all of the conditions are satisfied. */
public class AndCompoundTC implements TerminalCondition {
    private final List<TerminalCondition> tcList;

    public AndCompoundTC(List<TerminalCondition> tcList) {
        this.tcList = new ArrayList<>(tcList);
    }

    public void add(TerminalCondition tc) {
        tcList.add(tc);
    }

    @Override
    public boolean isSatisfied(MetaHeuristic alg) {
        return tcList.stream().allMatch(tc -> tc.isSatisfied(alg));
    }

    @Override
    public void init() {
        tcList.forEach(TerminalCondition::init);
    }
}
