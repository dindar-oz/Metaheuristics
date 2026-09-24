package metaheuristic.ls;

import base.MutationOperator;
import base.OptimizationProblem;
import base.TerminalCondition;
import metaheuristic.AbstractSMetaheuristic;
import representation.base.Individual;
import util.random.RandUtil;

import java.util.List;

/**
 * Stochastic hill climbing: a random neighbor is accepted only if it is better.
 * Stops when maxNonImproving neighbors in a row fail to improve, or when the terminal condition is satisfied.
 */
public class LocalSearch extends AbstractSMetaheuristic {

    private final List<MutationOperator> neighboringFunctions;
    private final int maxNonImproving;

    public LocalSearch(List<MutationOperator> neighboringFunctions, int maxNonImproving) {
        this(neighboringFunctions, maxNonImproving, TerminalCondition.never());
    }

    public LocalSearch(List<MutationOperator> neighboringFunctions, int maxNonImproving, TerminalCondition terminalCondition) {
        super(terminalCondition);
        if (neighboringFunctions.isEmpty())
            throw new IllegalArgumentException("LocalSearch needs at least one neighboring function");
        this.neighboringFunctions = neighboringFunctions;
        this.maxNonImproving = maxNonImproving;
    }

    @Override
    protected void search(OptimizationProblem problem) {
        int nonImproving = 0;
        while (nonImproving < maxNonImproving && !isTerminated()) {
            MutationOperator nf = neighboringFunctions.get(RandUtil.randInt(neighboringFunctions.size()));
            Individual neighbor = neighbor(problem, nf, currentSolution);

            if (neighbor.getCost() < currentSolution.getCost()) {
                currentSolution = neighbor;
                nonImproving = 0;
            } else
                nonImproving++;

            iterationCount++;
            fireIterationEvent();
        }
    }

    @Override
    protected String defaultName() {
        return "LocalSearch";
    }
}
