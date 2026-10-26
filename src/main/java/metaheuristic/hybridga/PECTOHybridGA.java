package metaheuristic.hybridga;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.operators.RandomPECTOSG;

/**
 * The research hybrid GA for PECTO (research/ga/pecto/PECTOGA), set up on the framework's PECTO classes:
 * <ul>
 * <li>initial population: half random ({@link RandomPECTOSG}), a quarter partly greedy ({@link PartialGreedyPECTOSG}),
 *     a quarter randomized greedy ({@link RandomizedGreedyPECTOSG}); the migrants come from these three in turn;</li>
 * <li>crossover: {@link AlternatingRouteCrossover} (ARX), one child per pair of parents;</li>
 * <li>mutation of the offspring: {@link CostliestRouteMove};</li>
 * <li>perturbation on stagnation, local search and simulated annealing neighbors: {@link SwapParticipants}.</li>
 * </ul>
 * The other parameters keep the defaults of {@link HybridGA.Builder}, the values of the research code, and can be
 * changed on the returned builder:
 * <pre>
 * HybridGA&lt;PECTOSolution&gt; ga = PECTOHybridGA.builder(problem).populationSize(60).hybrid(false).build();
 * RunResult&lt;PECTOSolution&gt; result = Runner.of(ga).seed(1).stopWhen(new EvaluationBasedTC(100_000)).run(problem);
 * </pre>
 * The operators keep every solution feasible, so the best solution of a run is feasible.
 */
public final class PECTOHybridGA {

    private PECTOHybridGA() {
    }

    public static HybridGA.Builder<PECTOSolution> builder(PECTOProblem problem) {
        SwapParticipants swap = new SwapParticipants();
        return HybridGA.builder(new AlternatingRouteCrossover())
                .source(new RandomPECTOSG(problem), 0.5)
                .source(new PartialGreedyPECTOSG(problem), 0.25)
                .source(new RandomizedGreedyPECTOSG(problem), 0.25)
                .mutation(new CostliestRouteMove())
                .perturbation(swap)
                .refinement(swap);
    }
}
