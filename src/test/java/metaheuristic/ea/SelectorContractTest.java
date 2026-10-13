package metaheuristic.ea;

import metaheuristic.ea.base.ParentSelector;
import metaheuristic.ea.base.VictimSelector;
import metaheuristic.ea.parentselector.CostBasedParentSelector;
import metaheuristic.ea.parentselector.RouletteWheelParentSelector;
import metaheuristic.ea.parentselector.TournamentParentSelector;
import metaheuristic.ea.victimselector.RouletteWheelVictimSelector;
import metaheuristic.ea.victimselector.SimpleVictimSelector;
import org.junit.jupiter.api.Test;
import representation.IntegerPermutation;
import representation.base.Individual;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Selectors must not modify the population list, must select distinct members and keep no state between calls.
 */
class SelectorContractTest {

    private static List<Individual<IntegerPermutation>> population(int size, RNG rng) {
        List<Individual<IntegerPermutation>> pop = new ArrayList<>();
        for (int i = 0; i < size; i++)
            pop.add(new Individual<>(new IntegerPermutation(new int[]{i}), rng.randDouble() * 100));
        return pop;
    }

    private static void checkSelection(List<Individual<IntegerPermutation>> pop, List<Individual<IntegerPermutation>> before,
                                       List<Individual<IntegerPermutation>> selected, int expectedSize) {
        assertEquals(before, pop, "input list was modified");
        assertEquals(expectedSize, selected.size());
        Set<Individual<IntegerPermutation>> distinct = Collections.newSetFromMap(new IdentityHashMap<>());
        distinct.addAll(selected);
        assertEquals(selected.size(), distinct.size(), "duplicates selected");
        assertTrue(pop.containsAll(selected));
    }

    @Test
    void parentSelectors() {
        RNG rng = new MersenneTwisterRNG(1);
        for (ParentSelector s : Arrays.asList(new CostBasedParentSelector(), new RouletteWheelParentSelector(),
                new TournamentParentSelector(3), new TournamentParentSelector(100, 4))) {
            // population size changes between calls: "half of the population" must follow it
            for (int size : new int[]{10, 20, 7}) {
                List<Individual<IntegerPermutation>> pop = population(size, rng);
                List<Individual<IntegerPermutation>> before = new ArrayList<>(pop);
                int expected = s instanceof TournamentParentSelector && s.toString().contains(", 4") ? 4 : size / 2;
                checkSelection(pop, before, s.selectParents(pop, rng), expected);
            }
        }
    }

    @Test
    void victimSelectors() {
        RNG rng = new MersenneTwisterRNG(2);
        for (VictimSelector s : Arrays.asList(new SimpleVictimSelector(), new RouletteWheelVictimSelector())) {
            List<Individual<IntegerPermutation>> pop = population(15, rng);
            List<Individual<IntegerPermutation>> before = new ArrayList<>(pop);
            checkSelection(pop, before, s.selectVictims(pop, 5, rng), 5);
        }
    }

    @Test
    void costBasedSelectsBestAndSimpleVictimSelectsWorst() {
        RNG rng = new MersenneTwisterRNG(3);
        List<Individual<IntegerPermutation>> pop = population(10, rng);
        List<Individual<IntegerPermutation>> sorted = new ArrayList<>(pop);
        sorted.sort(Individual.BY_COST);
        assertEquals(sorted.subList(0, 3), new CostBasedParentSelector(3).selectParents(pop, rng));
        List<Individual<IntegerPermutation>> worst = new SimpleVictimSelector().selectVictims(pop, 2, rng);
        assertEquals(Arrays.asList(sorted.get(9), sorted.get(8)), worst);
    }
}
