package metaheuristic.hybridga;

import metaheuristic.sa.GeometricCooling;
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
 * {@link #builder} is the reduced GA, the recommended default: the mechanisms that the ablation experiments
 * (pecto-hga-vs-aco parts 5 and 6, pecto-hga-reduced-r2) found harmful or removable are off. Parents come from the
 * tournament only, and there is no offspring mutation, no stagnation response and no annealing. What remains is the
 * mixed seeding, ARX, the local search of every offspring and age-protected replacement. {@link #researchBuilder} is
 * the full research GA, with every mechanism on and the values of the research code. Every mechanism can be switched
 * back on with the builder; on the reduced GA:
 * <pre>
 * .parentSelectors(null, null, 0.75)                          // roulette on 1 / cost with probability 0.75
 * .mutationRate(0.05).mutationRateAdaptation(1.1, 0.05, 0.75) // offspring mutation, its rate growing on stagnation
 * .stagnationPeriod(100)                                       // migrants and perturbation after 100 stagnant generations
 * .annealing(25, 0.1, new GeometricCooling(0.95), 1)           // annealing of the best individual
 * </pre>
 * For example:
 * <pre>
 * HybridGA&lt;PECTOSolution&gt; ga = PECTOHybridGA.builder(problem).populationSize(60).build();
 * RunResult&lt;PECTOSolution&gt; result = Runner.of(ga).seed(1).stopWhen(new EvaluationBasedTC(100_000)).run(problem);
 * </pre>
 * The operators keep every solution feasible, so the best solution of a run is feasible.
 */
public final class PECTOHybridGA {

    private PECTOHybridGA() {
    }

    // the reduced GA: tournament only, no offspring mutation, no stagnation response, no annealing
    public static HybridGA.Builder<PECTOSolution> builder(PECTOProblem problem) {
        return researchBuilder(problem)
                .parentSelectors(null, null, 0)
                .mutationRate(0).mutationRateAdaptation(1, 0, 0)
                .stagnationPeriod(0)
                .annealing(25, 0.1, new GeometricCooling(0.95), 0);
    }

    // the full research GA: every mechanism on, with the defaults of HybridGA.Builder
    public static HybridGA.Builder<PECTOSolution> researchBuilder(PECTOProblem problem) {
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
