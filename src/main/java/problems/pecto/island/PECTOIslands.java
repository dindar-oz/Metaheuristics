package problems.pecto.island;

import metaheuristic.hybridga.HybridGA;
import metaheuristic.hybridga.PECTOHybridGA;
import metaheuristic.island.IslandModel;
import metaheuristic.island.MigrantSelector;
import metaheuristic.island.MigrationTrigger;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.aco.PECTOACO;

/**
 * Two islands for PECTO, an ant colony ({@link PECTOACO}, island {@value #ACO}) and the hybrid GA
 * ({@link PECTOHybridGA}, island {@value #GA}), with two channels:
 * <ul>
 * <li><b>{@value #ACO} &gt; {@value #GA}</b>: when the colony's best is better than the GA's, or when the GA has been
 *     stuck for {@value #STAGNATION} generations, up to {@value #MIGRANTS} good ants (costlier than the colony's best
 *     offered solution by at most the fraction {@value #MAX_GAP}) that differ most from the GA's population and from each other take the
 *     places of duplicates and old individuals in the GA;</li>
 * <li><b>{@value #GA} &gt; {@value #ACO}</b>: when the GA's best is better than the colony's, it deposits pheromone
 *     (PECTOACO's immigrantDeposit), so the ants search around it too; the colony keeps its own best.</li>
 * </ul>
 * Both "better" mean by more than the fraction {@value #MARGIN}. By default the colony makes one iteration and the GA
 * {@value #GA_STEPS} generations per round, about the same time on the large Table I instances
 * (pecto-best-aco-vs-hga, 600 s: 29 generations per iteration on T8, 39 on T10). The colony has its tuned defaults;
 * the GA is the reduced GA with local search 30, the best GA under time budgets so far.
 */
public final class PECTOIslands {

    public static final String ACO = "aco", GA = "ga";
    public static final int GA_STEPS = 35;
    public static final int GA_LOCAL_SEARCH = 30;
    public static final int STAGNATION = 50;
    public static final int MIGRANTS = 4;
    public static final double MAX_GAP = 0.1;
    public static final double MARGIN = 0.001;

    private PECTOIslands() {
    }

    // the two islands with their default configurations and both channels
    public static IslandModel.Builder<PECTOSolution, PECTOProblem> builder(PECTOProblem problem) {
        return islands(PECTOACO.builder().build(),
                PECTOHybridGA.builder(problem).localSearchSteps(GA_LOCAL_SEARCH).build(), GA_STEPS)
                .channel(ACO, GA, acoToGa(MARGIN, STAGNATION), MigrantSelector.diverse(MIGRANTS, MAX_GAP))
                .channel(GA, ACO, MigrationTrigger.whenBetter(MARGIN), MigrantSelector.best(1));
    }

    // the two islands, the colony making one iteration and the GA `gaSteps` generations per round; no channels yet
    public static IslandModel.Builder<PECTOSolution, PECTOProblem> islands(PECTOACO aco, HybridGA<PECTOSolution> ga,
                                                                          int gaSteps) {
        return IslandModel.<PECTOSolution, PECTOProblem>builder()
                .name("PECTO islands")
                .island(ACO, aco, 1)
                .island(GA, ga, gaSteps);
    }

    // the colony is better by more than `margin`, or (if stagnation > 0) the GA has been stuck for `stagnation` generations
    public static MigrationTrigger acoToGa(double margin, int stagnation) {
        MigrationTrigger better = MigrationTrigger.whenBetter(margin);
        return stagnation > 0 ? better.or(MigrationTrigger.stagnation(stagnation)) : better;
    }
}
