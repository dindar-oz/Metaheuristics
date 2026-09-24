package metaheuristic;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import base.TerminalCondition;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.List;

/**
 * Common bookkeeping for all metaheuristics: best-so-far tracking, evaluation counting,
 * termination and listener notification. Subclasses implement run().
 */
public abstract class AbstractMetaheuristic implements MetaHeuristic {

    protected TerminalCondition terminalCondition = TerminalCondition.never();
    protected int iterationCount = 0;

    private String name = null;
    private long neighboringCount = 0;
    private double bestKnownCost = Double.MAX_VALUE;
    private Representation bestKnownSolution = null;
    private long startTime;
    private long bestAchieveTime;

    private final List<MetaHeuristicListener> listeners = new ArrayList<>();

    protected AbstractMetaheuristic() {
    }

    protected AbstractMetaheuristic(TerminalCondition terminalCondition) {
        this.terminalCondition = terminalCondition;
    }

    @Override
    public final void perform(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator) {
        init(problem);
        run(problem, solutionGenerator);
        listeners.forEach(MetaHeuristicListener::onCompleteEvent);
    }

    protected abstract void run(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator);

    // Resets the state of the algorithm so that it can be performed again
    protected void init(OptimizationProblem problem) {
        iterationCount = 0;
        neighboringCount = 0;
        bestKnownSolution = null;
        bestKnownCost = Double.MAX_VALUE;
        startTime = System.currentTimeMillis();
        bestAchieveTime = 0;
        terminalCondition.init();
    }

    protected boolean isTerminated() {
        return terminalCondition.isSatisfied(this);
    }

    // Evaluates the solution, counts the evaluation and updates the best known solution
    protected double evaluate(OptimizationProblem problem, Representation r) {
        neighboringCount++;
        double cost = problem.cost(r);
        updateBestIfNecessary(r, cost);
        return cost;
    }

    protected void updateBestIfNecessary(Representation solution, double cost) {
        if (cost < bestKnownCost) {
            bestKnownSolution = solution.clone();
            bestKnownCost = cost;
            bestAchieveTime = System.currentTimeMillis() - startTime;
        }
    }

    public void setTerminalCondition(TerminalCondition terminalCondition) {
        this.terminalCondition = terminalCondition;
    }

    public void addListener(MetaHeuristicListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MetaHeuristicListener listener) {
        listeners.remove(listener);
    }

    protected boolean hasListeners() {
        return !listeners.isEmpty();
    }

    protected void fireIterationEvent(IterationEvent event) {
        for (MetaHeuristicListener listener : listeners)
            listener.onIterationEvent(event);
    }

    protected String defaultName() {
        return getClass().getSimpleName();
    }

    @Override
    public String getName() {
        return name == null ? defaultName() : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public int getIterationCount() {
        return iterationCount;
    }

    @Override
    public long getNeighboringCount() {
        return neighboringCount;
    }

    @Override
    public double getBestKnownCost() {
        return bestKnownCost;
    }

    @Override
    public Representation getBestKnownSolution() {
        return bestKnownSolution;
    }

    @Override
    public long getBestAchieveTime() {
        return bestAchieveTime;
    }

    @Override
    public String toString() {
        return getName();
    }
}
