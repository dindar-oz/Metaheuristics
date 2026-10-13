package problems.wflop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import representation.DoubleVector;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the implementation against Yu &amp; Zhang (2024), "A teaching-learning-based optimization algorithm with
 * reinforcement learning to address wind farm layout optimization problem", Applied Soft Computing 151, 111135.
 */
class WFLOPTest {

    // Best layouts found by RLPS-TLBO under Wind Condition 1 (Figs. 16k-19k), keyed by row y -> column x centers [m]
    static final Map<Integer, int[]> CASE1 = rows(
            1900, new int[]{100, 500, 700, 900, 1100, 1300, 1500, 1700, 1900}, 1700, new int[]{100, 1700},
            1500, new int[]{100, 700, 1300, 1900}, 1300, new int[]{100, 1900}, 1100, new int[]{100, 500, 1300, 1500, 1900},
            900, new int[]{100, 700, 1100, 1900}, 700, new int[]{100, 300, 1700}, 500, new int[]{100, 700, 1300, 1900},
            300, new int[]{100, 1700}, 100, new int[]{100, 300, 500, 700, 900, 1300, 1500, 1900});
    static final Map<Integer, int[]> CASE2 = rows(
            1900, new int[]{100, 300, 500, 700, 900, 1100, 1300, 1500, 1900}, 1700, new int[]{100, 1900},
            1500, new int[]{100, 700, 1900}, 1300, new int[]{900, 1900}, 1100, new int[]{100, 900, 1500, 1900},
            900, new int[]{100, 700, 1900}, 700, new int[]{100, 900, 1500, 1900}, 500, new int[]{100, 500, 1300, 1900},
            300, new int[]{100, 700, 1900}, 100, new int[]{100, 300, 700, 900, 1100, 1300, 1500, 1700, 1900});
    static final Map<Integer, int[]> CASE3 = rows(
            1900, new int[]{100, 500, 700, 1100, 1300, 1700, 1900}, 1700, new int[]{100, 1900},
            1500, new int[]{100, 500, 1100, 1900}, 1300, new int[]{100, 1900}, 1100, new int[]{100, 1300, 1900},
            900, new int[]{100, 500, 1100, 1900}, 700, new int[]{100, 1900}, 500, new int[]{100, 300, 900, 1900},
            300, new int[]{100, 1100, 1300, 1900}, 100, new int[]{100, 300, 500, 700, 1100, 1700, 1900});
    static final Map<Integer, int[]> CASE4 = rows(
            1900, new int[]{100, 500, 700, 1100, 1300, 1700, 1900}, 1700, new int[]{100, 500, 1900},
            1500, new int[]{100, 1700, 1900}, 1300, new int[]{100, 700, 900, 1300, 1900}, 1100, new int[]{100, 1900},
            900, new int[]{100, 900, 1900}, 700, new int[]{100, 300, 700, 1900}, 500, new int[]{100, 1300, 1900},
            300, new int[]{100, 1900}, 100, new int[]{100, 300, 700, 1100, 1300, 1500, 1900});

    private static Map<Integer, int[]> rows(Object... pairs) {
        Map<Integer, int[]> m = new LinkedHashMap<>();
        for (int k = 0; k < pairs.length; k += 2)
            m.put((Integer) pairs[k], (int[]) pairs[k + 1]);
        return m;
    }

    static int[] cells(WindFarm farm, Map<Integer, int[]> rows) {
        List<Integer> cells = new ArrayList<>();
        rows.forEach((y, xs) -> {
            for (int x : xs)
                cells.add(farm.cellAt(x, y));
        });
        return cells.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void checkReported(WFLOPCase c, Map<Integer, int[]> layout, int n, double power, double cost, double efficiencyPercent) {
        WindFarm farm = c.create(WindScenario.condition1()).farm();
        LayoutEvaluation e = farm.evaluate(cells(farm, layout));
        assertEquals(n, e.turbines());
        assertEquals(power, e.power(), 1e-3, "power of " + c);
        assertEquals(cost, e.cost(), 1e-4, "cost of " + c);
        assertEquals(efficiencyPercent, 100 * e.efficiency(), 0.005, "efficiency of " + c);
        assertEquals(cost / power, e.objective(), 5e-7);     // Tables 6-9 report 6 decimals
    }

    // Tables 6-9 and Figs. 16-19, row RLPS-TLBO
    @Test
    void reproducesReportedCase1() {
        checkReported(WFLOPCase.CASE_1, CASE1, 43, 20021.2340, 29.2409, 89.82);
    }

    @Test
    void reproducesReportedCase2() {
        checkReported(WFLOPCase.CASE_2, CASE2, 43, 20662.1064, 29.2409, 92.69);
    }

    @Test
    void reproducesReportedCase3() {
        checkReported(WFLOPCase.CASE_3, CASE3, 39, 18372.2521, 26.9216, 90.87);
    }

    @Test
    void reproducesReportedCase4() {
        checkReported(WFLOPCase.CASE_4, CASE4, 39, 18903.3231, 26.9216, 93.50);
    }

    @Test
    void parametersMatchTable5() {
        WindFarmParameters p = WindFarmParameters.paper();
        assertEquals(0.5 / Math.log(60 / 0.3), p.wakeDecay(), 1e-15);
        assertEquals(1 - Math.sqrt(0.12), p.initialDeficit(), 1e-15);
        assertEquals(10, p.cellsPerSide());
        assertEquals(0.5 * 0.4 * 1.225 * Math.PI * 400 / 1000, p.powerCoefficient(), 1e-12);
    }

    @Test
    void singleTurbineHasNoWakeLoss() {
        WindFarm farm = WFLOPCase.CASE_1.create(WindScenario.condition2()).farm();
        LayoutEvaluation e = farm.evaluate(new int[]{55});
        assertEquals(farm.wakeFreePower(1), e.power(), 1e-9);
        assertEquals(1.0, e.wakeEfficiency(), 1e-12);
    }

    @Test
    void alignedTurbinesFollowJensenFormula() {
        WindFarmParameters p = WindFarmParameters.paper();
        for (WakeMode mode : WakeMode.values()) {
            JensenWakeModel model = new JensenWakeModel(p, mode);
            // wind from the west (270 deg) blows east: a turbine 400 m east is fully inside the wake
            WindScenario.State west = new WindScenario.State(270, 12, 1);
            double rx = p.rotorRadius() + p.wakeDecay() * 400;
            double expected = p.initialDeficit() * Math.pow(p.rotorRadius() / rx, 2);
            assertEquals(expected, model.deficit(400, 0, west.flowX, west.flowY), 1e-12, mode.toString());
            assertEquals(0, model.deficit(-400, 0, west.flowX, west.flowY), "upstream turbines are not waked");
            assertEquals(0, model.deficit(0, 400, west.flowX, west.flowY), "side by side turbines are not waked");
        }
    }

    @Test
    void partialWakeIsScaledByOverlapArea() {
        WindFarmParameters p = WindFarmParameters.paper();
        JensenWakeModel full = new JensenWakeModel(p, WakeMode.FULL);
        JensenWakeModel partial = new JensenWakeModel(p, WakeMode.PARTIAL);
        WindScenario.State west = new WindScenario.State(270, 12, 1);
        double x = 1000, rx = p.rotorRadius() + p.wakeDecay() * x;
        double lateral = rx;   // rotor center on the wake edge: about half the rotor is waked
        double ratio = partial.deficit(x, lateral, west.flowX, west.flowY) / full.deficit(x, lateral, west.flowX, west.flowY);
        assertEquals(JensenWakeModel.circleOverlap(lateral, rx, p.rotorRadius()) / p.rotorArea(), ratio, 1e-12);
        assertTrue(ratio > 0.4 && ratio < 0.5, "a bit less than half of the rotor, was " + ratio);
    }

    @Test
    void circleOverlapCases() {
        assertEquals(0, JensenWakeModel.circleOverlap(10, 3, 4));
        assertEquals(Math.PI * 9, JensenWakeModel.circleOverlap(0.5, 3, 4), 1e-12);
        // two unit circles at distance 1: 2pi/3 - sqrt(3)/2
        assertEquals(2 * Math.PI / 3 - Math.sqrt(3) / 2, JensenWakeModel.circleOverlap(1, 1, 1), 1e-12);
    }

    // The sparse wake matrix must give the same power as a direct evaluation of Eqs. (4), (6) and (9)
    @ParameterizedTest
    @EnumSource(WakeMode.class)
    void tableMatchesDirectComputation(WakeMode mode) {
        WindScenario wind = WindScenario.condition2();
        WindFarm farm = new WindFarm(WindFarmParameters.paper(), mode, wind);
        JensenWakeModel model = farm.wakeModel();
        RNG rng = new MersenneTwisterRNG(5);
        for (int trial = 0; trial < 20; trial++) {
            int[] layout = rng.distinctInts(100, 1 + rng.randInt(60)).stream().mapToInt(Integer::intValue).toArray();
            double expected = 0;
            for (WindScenario.State s : wind.states())
                for (int j : layout) {
                    double sum = 0;
                    for (int i : layout)
                        if (i != j) {
                            double d = model.deficit(farm.centerX(j) - farm.centerX(i), farm.centerY(j) - farm.centerY(i), s.flowX, s.flowY);
                            sum += d * d;
                        }
                    double v = s.speed * (1 - Math.sqrt(sum));
                    expected += s.probability * 0.5 * 0.4 * 1.225 * Math.PI * 400 * v * v * v / 1000;
                }
            assertEquals(expected, farm.power(layout), 1e-9 * expected);
        }
    }

    @Test
    void turbinePowersAddUpToFarmPower() {
        WindFarm farm = WFLOPCase.CASE_2.create(WindScenario.condition2()).farm();
        int[] layout = cells(farm, CASE2);
        double sum = 0;
        for (double p : farm.turbinePowers(layout, -1))
            sum += p;
        assertEquals(farm.power(layout), sum, 1e-12 * sum);
        double byState = 0;
        for (int s = 0; s < farm.stateCount(); s++)
            for (double p : farm.turbinePowers(layout, s))
                byState += p / farm.stateCount();   // equally likely states
        assertEquals(farm.power(layout), byState, 1e-12 * byState);
        assertEquals(farm.wakeFreePower(1), farm.freeTurbinePower(-1), 1e-12);
    }

    @Test
    void variableEncodingRoundsToOccupancy() {
        VariableTurbineWFLOP problem = (VariableTurbineWFLOP) WFLOPCase.CASE_1.create(WindScenario.condition1());
        double[] x = new double[100];
        x[3] = 0.5;     // rounds to 1
        x[7] = 0.49;    // rounds to 0
        x[42] = 1.0;
        assertArrayEquals(new int[]{3, 42}, problem.decode(new DoubleVector(x)));
        assertTrue(problem.isFeasible(new DoubleVector(x)));
        assertEquals(WFLOPProblem.INFEASIBLE_COST, problem.cost(new DoubleVector(new double[100])));
        assertFalse(problem.isFeasible(new DoubleVector(new double[100])));
    }

    @Test
    void fixedEncodingFollowsFigure10() {
        FixedTurbineWFLOP problem = (FixedTurbineWFLOP) WFLOPCase.CASE_3.create(WindScenario.condition1(), 4);
        // 1 -> first free cell, again 1 -> the next one, 100 exceeds the free list -> last free cell (twice)
        assertArrayEquals(new int[]{0, 1, 99, 98}, problem.decode(new DoubleVector(new double[]{1, 1.2, 100, 99.7})));
        // 2.5 rounds to 3: the third free cell
        assertArrayEquals(new int[]{2, 0, 1, 3}, problem.decode(new DoubleVector(new double[]{2.5, 1, 1, 1})));
    }

    @Test
    void fixedEncodingAlwaysGivesDistinctCells() {
        FixedTurbineWFLOP problem = (FixedTurbineWFLOP) WFLOPCase.CASE_4.create(WindScenario.condition1());
        RNG rng = new MersenneTwisterRNG(9);
        for (int trial = 0; trial < 200; trial++) {
            int[] cells = problem.decode(problem.randomSolution(rng));
            assertEquals(39, cells.length);
            assertEquals(39, Arrays.stream(cells).distinct().count());
            assertTrue(problem.isFeasible(problem.randomSolution(rng)));
        }
    }

    @Test
    void costFunctionMatchesEquation8() {
        assertEquals(26.9216, WindFarm.cost(39), 1e-4);
        assertEquals(29.2409, WindFarm.cost(43), 1e-4);
        assertEquals(1.0, WindFarm.cost(1), 0.001);
    }

    @Test
    void condition2SpeedsBySector() {
        List<WindScenario.State> states = WindScenario.condition2().states();
        assertEquals(36, states.size());
        assertEquals(8, states.get(0).speed);      // 10 deg
        assertEquals(8, states.get(11).speed);     // 120 deg
        assertEquals(12, states.get(12).speed);    // 130 deg
        assertEquals(17, states.get(35).speed);    // 360 deg
        assertEquals((512 + 1728 + 4913) / 3.0, WindScenario.condition2().meanCubedSpeed(), 1e-9);
    }
}
