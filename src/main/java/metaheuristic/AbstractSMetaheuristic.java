package metaheuristic;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import base.TerminalCondition;
import representation.SimpleIndividual;
import representation.base.Individual;
import representation.base.Representation;

/**
 * Base class for single-solution (trajectory) metaheuristics.
 * The search starts from the solution given with setStartSolution(), or from a generated one.
 */
public abstract class AbstractSMetaheuristic extends AbstractMetaheuristic {

    protected Individual currentSolution = null;
    private Individual startSolution = null;

    protected AbstractSMetaheuristic() {
    }

    protected AbstractSMetaheuristic(TerminalCondition terminalCondition) {
        super(terminalCondition);
    }

    public void setStartSolution(Individual startSolution) {
        this.startSolution = startSolution;
    }

    public Individual getCurrentSolution() {
        return currentSolution;
    }

    @Override
    protected final void run(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator) {
        if (startSolution != null) {
            currentSolution = startSolution;
            updateBestIfNecessary(startSolution.getRepresentation(), startSolution.getCost());
        } else {
            Representation r = solutionGenerator.generate(problem);
            currentSolution = new SimpleIndividual(r, evaluate(problem, r));
        }
        search(problem);
    }

    protected abstract void search(OptimizationProblem problem);

    // Applies the operator to the solution and evaluates the resulting neighbor
    protected Individual neighbor(OptimizationProblem problem, MutationOperator operator, Individual solution) {
        Representation r = operator.apply(problem, solution.getRepresentation());
        return new SimpleIndividual(r, evaluate(problem, r));
    }

    protected void fireIterationEvent() {
        if (hasListeners())
            fireIterationEvent(new BaseSIterationEvent(iterationCount, getNeighboringCount(),
                    getBestKnownCost(), getBestKnownSolution(),
                    currentSolution.getCost(), currentSolution.getRepresentation()));
    }
}
