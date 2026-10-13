package problems.wflop.rl;

import metaheuristic.rl.FeaturedConstruction;
import org.junit.jupiter.api.Test;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.WFLOPCase;
import problems.wflop.WindFarm;
import problems.wflop.WindFarmParameters;
import problems.wflop.WindScenario;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import static org.junit.jupiter.api.Assertions.*;
import static problems.wflop.rl.LayoutFeature.*;

class ConstructiveWFLOPTest {

    private static final ConstructiveWFLOP FIXED =
            new ConstructiveWFLOP(WFLOPCase.CASE_4.createDiscrete(WindScenario.condition2(), 12));
    private static final ConstructiveWFLOP VARIABLE =
            new ConstructiveWFLOP(WFLOPCase.CASE_1.createDiscrete(WindScenario.condition1()));

    // The rewards of an episode telescope to -cost / obj*, so maximizing the return minimizes the WFLOP objective
    @Test
    void returnIsMinusScaledCost() {
        RNG rng = new MersenneTwisterRNG(1);
        for (ConstructiveWFLOP problem : new ConstructiveWFLOP[]{FIXED, VARIABLE})
            for (int episode = 0; episode < 20; episode++) {
                FeaturedConstruction<CellLayout> c = problem.start(rng);
                double sum = 0;
                while (!c.isComplete()) {
                    int[] actions = c.feasibleActions();
                    sum += c.step(actions[rng.randInt(actions.length)]);
                }
                CellLayout layout = c.solution();
                assertTrue(problem.isFeasible(layout));
                double expected = -problem.cost(layout) / problem.referenceObjective();
                assertEquals(expected, sum, 1e-12 * Math.abs(expected));
            }
    }

    // The power features, computed from the farm's wake matrix as turbines are placed, predict the change of the
    // farm's own evaluation exactly; and the objective improves exactly when MARGINAL_POWER > MARGINAL_COST
    @Test
    void powerFeaturesMatchTheFarm() {
        RNG rng = new MersenneTwisterRNG(2);
        for (ConstructiveWFLOP problem : new ConstructiveWFLOP[]{FIXED, VARIABLE}) {
            WindFarm farm = problem.problem().farm();
            for (int episode = 0; episode < 10; episode++) {
                FeaturedConstruction<CellLayout> c = problem.start(rng);
                while (!c.isComplete()) {
                    int[] actions = c.feasibleActions();
                    double[][] features = c.actionFeatures();
                    assertEquals(actions.length, features.length);
                    int k = rng.randInt(actions.length);
                    if (actions[k] == farm.cellCount()) {
                        c.step(actions[k]);
                        continue;
                    }
                    double before = c.solution().size() == 0 ? 0 : farm.power(c.solution().cells());
                    double reward = c.step(actions[k]);
                    double added = farm.power(c.solution().cells()) - before;
                    double predicted = (features[k][OWN_EFFICIENCY.ordinal()] - features[k][CAUSED_LOSS.ordinal()])
                            * farm.freeTurbinePower(-1);
                    assertEquals(added, predicted, 1e-9 * farm.freeTurbinePower(-1));
                    assertEquals(reward, features[k][IMPROVEMENT.ordinal()] / c.solution().size(), 1e-9);
                    if (c.solution().size() > 1 && Math.abs(reward) > 1e-12)
                        assertEquals(reward > 0,
                                features[k][MARGINAL_POWER.ordinal()] > features[k][MARGINAL_COST.ordinal()]);
                }
            }
        }
    }

    @Test
    void featuresAreBounded() {
        RNG rng = new MersenneTwisterRNG(3);
        FeaturedConstruction<CellLayout> c = VARIABLE.start(rng);
        for (int step = 0; step < 60 && !c.isComplete(); step++) {
            for (double[] row : c.actionFeatures())
                for (LayoutFeature f : LayoutFeature.values()) {
                    double v = row[f.ordinal()];
                    if (f == MARGINAL_POWER || f == MARGINAL_COST || f == IMPROVEMENT)
                        assertTrue(v >= -1 && v < 2, f + " = " + v);
                    else
                        assertTrue(v >= 0 && v <= 1 + 1e-9, f + " = " + v);
                }
            int[] actions = c.feasibleActions();
            c.step(actions[rng.randInt(actions.length - 1)]);    // never stop: the last action
        }
    }

    // A cell in the middle of an empty farm looks the same on a 10 x 10 and on a 20 x 20 farm (except that it is farther
    // from the edge on the larger one), and cells at the same place near a corner are as far from the edge on both
    @Test
    void featuresDoNotDependOnTheFarmSize() {
        double[][] small = emptyFarm(10), large = emptyFarm(20);
        for (LayoutFeature f : LayoutFeature.values())
            if (f != EDGE_DISTANCE)
                assertEquals(small[5 * 10 + 5][f.ordinal()], large[10 * 20 + 10][f.ordinal()], 0.05, f.name());
        assertEquals(small[1 * 10 + 2][EDGE_DISTANCE.ordinal()], large[1 * 20 + 2][EDGE_DISTANCE.ordinal()], 1e-12);
    }

    // the features of every cell of an empty side x side farm; all cells are free, so row k is cell k
    private static double[][] emptyFarm(int side) {
        DiscreteWFLOP problem = WFLOPCase.CASE_3.createDiscrete(
                WindFarmParameters.builder().farmSize(200.0 * side).build(), WindScenario.condition1(), side * side / 4);
        return new ConstructiveWFLOP(problem).start(null).actionFeatures();
    }

    @Test
    void fixedCountEndsAtTheCountWithoutStop() {
        FeaturedConstruction<CellLayout> c = FIXED.start(new MersenneTwisterRNG(4));
        for (int n = 0; n < 12; n++) {
            assertFalse(c.isComplete());
            int[] actions = c.feasibleActions();
            assertEquals(100 - n, actions.length);
            for (int a : actions)
                assertTrue(a < 100, "no stop action for a fixed count");
            c.step(actions[0]);
        }
        assertTrue(c.isComplete());
        assertEquals(0, c.feasibleActions().length);
        assertEquals(12, c.solution().size());
    }

    @Test
    void variableCountStopsAfterTheFirstTurbine() {
        DiscreteWFLOP problem = VARIABLE.problem();
        FeaturedConstruction<CellLayout> c = VARIABLE.start(new MersenneTwisterRNG(5));
        int[] first = c.feasibleActions();
        assertEquals(100, first.length);
        assertThrows(IllegalArgumentException.class, () -> c.step(100));
        c.step(55);
        int[] second = c.feasibleActions();
        assertEquals(100, second[second.length - 1]);
        assertEquals(1.0, c.actionFeatures()[second.length - 1][STOP.ordinal()]);
        assertThrows(IllegalArgumentException.class, () -> c.step(55));
        assertEquals(0, c.step(100));
        assertTrue(c.isComplete());
        assertEquals(1, c.solution().size());
        assertEquals(problem.cost(c.solution()), VARIABLE.referenceObjective(), 1e-15);
    }
}
