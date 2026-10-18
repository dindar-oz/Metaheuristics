package core.algorithm.aco.problem.cfp;

import core.algorithm.aco.PheromoneMatrix;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.coalitionFormation.MultiCoalition;
import core.representation.IntegerAssignment;

/**
 * Pheromone for the multi-task coalition formation problem: an
 * {@code agents x getTaskCount()} matrix where cell {@code (a, t)} rewards assigning agent
 * {@code a} to task {@code t}. Column 0 is the model's dummy task 0, meaning "not assigned to
 * any task"; the real tasks are columns {@code 1 .. getTaskCount() - 1}. Each solution
 * deposits {@code 1 / cost} on its assignments.
 */
public class MCFPPheromoneMatrix extends PheromoneMatrix {

    /** Pass as {@code initialValue} to derive it from the instance (see {@link #withAutoInit}). */
    public static final double AUTO_INITIAL_VALUE = 0;

    /**
     * @param initialValue initial pheromone; any value {@code <= 0} means
     *                     {@code colonySize / C_ref} (see {@link #withAutoInit})
     */
    public MCFPPheromoneMatrix(double initialValue, int colonySize, double evaporationRatio) {
        super(initialValue, colonySize, evaporationRatio);
    }

    /**
     * Matrix whose initial pheromone is {@code colonySize / C_ref}, where {@code C_ref} is the
     * cost of a simple round-robin assignment. This puts the initial trail on the same scale as
     * the {@code 1 / cost} deposits (as {@code tau0 = m / C_nn} does for the TSP). A fixed
     * value such as 10 is millions of times larger than the deposits of instances whose costs
     * run into the hundreds of thousands, so the ants would never learn anything.
     */
    public static MCFPPheromoneMatrix withAutoInit(int colonySize, double evaporationRatio) {
        return new MCFPPheromoneMatrix(AUTO_INITIAL_VALUE, colonySize, evaporationRatio);
    }

    @Override
    public void init(OptimizationProblem problem) {
        MCFPModel mcfp = (MCFPModel) problem.model();
        double tau0 = initialValue > 0 ? initialValue : colonySize / referenceCost(problem, mcfp);
        // getTaskCount() already includes the dummy "unassigned" task 0.
        allocate(mcfp.getAgentCount(), mcfp.getTaskCount(), tau0);
    }

    /** Cost of assigning agent {@code i} to real task {@code 1 + i mod (tasks - 1)}. */
    private static double referenceCost(OptimizationProblem problem, MCFPModel mcfp) {
        int realTasks = mcfp.getTaskCount() - 1;
        int[] assignment = new int[mcfp.getAgentCount()];
        for (int a = 0; a < assignment.length; a++) {
            assignment[a] = 1 + a % realTasks;
        }
        MultiCoalition reference = new MultiCoalition(new IntegerAssignment(assignment), mcfp.getAgents(), mcfp.getTaskCount());
        return problem.objectiveValue(reference);
    }

    @Override
    protected void deposit(OptimizationProblem problem, Solution s) {
        MultiCoalition mc = (MultiCoalition) s.getRepresentation();
        IntegerAssignment assignment = mc.getCoalitionAssignment();
        double delta = 1.0 / s.objectiveValue();
        for (int agent = 0; agent < assignment.getLength(); agent++) {
            add(index(agent, assignment.get(agent)), delta);
        }
    }

    @Override
    protected PheromoneMatrix create(int colonySize) {
        return new MCFPPheromoneMatrix(initialValue, colonySize, evaporationRatio);
    }
}
