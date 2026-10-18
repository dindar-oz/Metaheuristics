package core.algorithm.aco.problem.wsn;

import core.algorithm.SimpleSolution;
import core.algorithm.aco.BaseAnt;
import core.algorithm.aco.PheromoneMatrix;
import core.algorithm.aco.PheromoneTrails;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.wsn.WSN;
import core.representation.BitString;
import core.utils.random.RNG;
import core.utils.random.RandUtils;

/**
 * Ant for the wireless sensor network deployment problem (minimise active sensors while
 * keeping every target k-covered and every sensor m-connected).
 * <p>
 * Starting with every sensor off, each step switches on one sensor chosen from a candidate set:
 * <ul>
 *   <li>with probability {@code pheromoneSelectionRate} by roulette on the candidates'
 *       pheromone (see {@link SelectionBias}),</li>
 *   <li>otherwise uniformly at random (exploration).</li>
 * </ul>
 * Construction stops once the network is feasible or no candidate is left.
 */
public class WSNAnt extends BaseAnt {

    /** Where the candidate sensors of each step come from. */
    public enum CandidateStrategy {
        /** Only sensors proposed by the coverage/connectivity repair heuristic ({@link WSNHeuristic}). */
        REPAIR_HEURISTIC,
        /** Every sensor that is still off (the former {@code WSNSimpleAnt}). */
        ALL_IDLE
    }

    /** How pheromone values turn into selection probabilities. */
    public enum SelectionBias {
        /** Standard ACO: probability proportional to pheromone, so sensors used by good solutions are favoured. */
        PROPORTIONAL,
        /**
         * Probability decreases with pheromone. This was the original behaviour; it steers ants
         * away from sensors used by good solutions and is kept only for comparison.
         */
        INVERSE
    }

    private final double pheromoneSelectionRate;
    private final CandidateStrategy candidateStrategy;
    private final SelectionBias selectionBias;

    private PheromoneMatrix pheromone;
    private WSNConstructionState state;
    private WSNHeuristic heuristic;
    private boolean exhausted;

    // Reused buffers.
    private int[] candidates;
    private double[] weights;

    /** Repair-heuristic ant with standard (proportional) pheromone selection. */
    public WSNAnt(double pheromoneSelectionRate) {
        this(pheromoneSelectionRate, CandidateStrategy.REPAIR_HEURISTIC, SelectionBias.PROPORTIONAL);
    }

    /**
     * @param pheromoneSelectionRate probability of choosing by pheromone instead of uniformly at random
     */
    public WSNAnt(double pheromoneSelectionRate, CandidateStrategy candidateStrategy, SelectionBias selectionBias) {
        this.pheromoneSelectionRate = pheromoneSelectionRate;
        this.candidateStrategy = candidateStrategy;
        this.selectionBias = selectionBias;
    }

    @Override
    public void init(OptimizationProblem problem, PheromoneTrails pheromoneTrails, RNG rng) {
        super.init(problem, pheromoneTrails, rng);
        WSN wsn = (WSN) problem.model();
        WSNData data = WSNData.of(wsn);           // shared by all ants on this instance
        pheromone = requireMatrix(pheromoneTrails, WSNAnt.class);
        state = new WSNConstructionState(data);
        heuristic = new WSNHeuristic(data, rng);
        candidates = new int[data.positionsSize()];
        weights = new double[data.positionsSize()];
    }

    @Override
    protected void reset() {
        state.reset();
        heuristic.reshuffle();
        exhausted = false;
    }

    @Override
    protected void next() {
        int count = candidateStrategy == CandidateStrategy.REPAIR_HEURISTIC
                ? heuristic.candidates(state, candidates)
                : idleSensors();

        if (count == 0) {
            exhausted = true; // nothing left that could improve feasibility
            return;
        }

        int choice;
        if (rng.randDouble() < pheromoneSelectionRate) {
            // WSN trails are a single row, so the flat index of sensor s is s itself.
            pheromone.read(candidates, count, weights);
            choice = selectionBias == SelectionBias.PROPORTIONAL
                    ? RandUtils.rouletteSelect(rng, weights, count)
                    : RandUtils.rouletteSelectInverse(rng, weights, count);
        } else {
            choice = rng.randInt(count);
        }
        state.turnOn(candidates[choice]);
    }

    private int idleSensors() {
        int count = 0;
        for (int s = 0; s < state.positionsSize(); s++) {
            if (!state.isOn(s)) candidates[count++] = s;
        }
        return count;
    }

    @Override
    protected boolean solutionConstructed() {
        return exhausted || state.isFeasible() || state.allOn();
    }

    @Override
    protected Solution buildSolution() {
        BitString bs = state.toBitString();
        return new SimpleSolution(bs, problem.objectiveValue(bs));
    }
}
