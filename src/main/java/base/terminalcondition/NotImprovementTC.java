package base.terminalcondition;

import base.TerminalCondition;
import metaheuristic.MetaHeuristic;

/** Stops when the best cost has not improved for the given number of checks. */
public class NotImprovementTC implements TerminalCondition {
    private static final double IMPROVEMENT_THRESHOLD = 0.00001;

    private final int nonImprovementCredit;
    private double previousBestCost;
    private int nonImprovementCounter;

    public NotImprovementTC(int nonImprovementCredit) {
        this.nonImprovementCredit = nonImprovementCredit;
    }

    @Override
    public boolean isSatisfied(MetaHeuristic alg) {
        if (previousBestCost - alg.getBestKnownCost() < IMPROVEMENT_THRESHOLD)
            nonImprovementCounter++;
        else {
            nonImprovementCounter = 0;
            previousBestCost = alg.getBestKnownCost();
        }
        return nonImprovementCounter > nonImprovementCredit;
    }

    @Override
    public void init() {
        nonImprovementCounter = 0;
        previousBestCost = Double.MAX_VALUE;
    }
}
