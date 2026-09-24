package base.terminalcondition;

import base.TerminalCondition;
import metaheuristic.MetaHeuristic;

import java.util.ArrayList;
import java.util.List;

/** Satisfied when any of the conditions is satisfied. */
public class OrCompoundTC implements TerminalCondition {
    private final List<TerminalCondition> tcList;

    public OrCompoundTC(List<TerminalCondition> tcList) {
        this.tcList = new ArrayList<>(tcList);
    }

    public void add(TerminalCondition tc) {
        tcList.add(tc);
    }

    @Override
    public boolean isSatisfied(MetaHeuristic alg) {
        return tcList.stream().anyMatch(tc -> tc.isSatisfied(alg));
    }

    @Override
    public void init() {
        tcList.forEach(TerminalCondition::init);
    }
}
