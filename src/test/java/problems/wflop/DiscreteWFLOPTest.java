package problems.wflop;

import base.MutationOperator;
import metaheuristic.ea.base.CrossOverOperator;
import org.junit.jupiter.api.Test;
import problems.wflop.operators.AddTurbine;
import problems.wflop.operators.MoveTurbineToNeighbor;
import problems.wflop.operators.RemoveTurbine;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DiscreteWFLOPTest {

    // The discrete problem must score the paper's layouts exactly like the validated continuous model
    @Test
    void scoresPaperLayoutsLikeTheWindFarm() {
        WindScenario wind = WindScenario.condition1();
        DiscreteWFLOP case1 = WFLOPCase.CASE_1.createDiscrete(wind);
        DiscreteWFLOP case4 = WFLOPCase.CASE_4.createDiscrete(wind);
        CellLayout layout1 = new CellLayout(10, WFLOPTest.cells(case1.farm(), WFLOPTest.CASE1));
        CellLayout layout4 = new CellLayout(10, WFLOPTest.cells(case4.farm(), WFLOPTest.CASE4));
        assertEquals(20021.2340, case1.evaluate(layout1).power(), 1e-3);
        assertEquals(18903.3231, case4.evaluate(layout4).power(), 1e-3);
        assertEquals(case1.farm().evaluate(layout1.cells()).objective(), case1.cost(layout1), 1e-15);
        assertEquals(case4.farm().evaluate(layout4.cells()).objective(), case4.cost(layout4), 1e-15);
    }

    @Test
    void feasibilityFollowsTheTurbineCount() {
        DiscreteWFLOP fixed = WFLOPCase.CASE_3.createDiscrete(WindScenario.condition1(), 5);
        DiscreteWFLOP variable = WFLOPCase.CASE_1.createDiscrete(WindScenario.condition1());
        CellLayout five = new CellLayout(10, new int[]{1, 2, 3, 4, 5});
        CellLayout four = five.withRemoved(5);
        assertTrue(fixed.isFeasible(five));
        assertFalse(fixed.isFeasible(four));
        assertEquals(WFLOPProblem.INFEASIBLE_COST, fixed.cost(four));
        assertTrue(variable.isFeasible(four));
        assertFalse(variable.isFeasible(new CellLayout(10, new int[0])));
    }

    @Test
    void neighborhoodKeepsLayoutsFeasible() {
        RNG rng = new MersenneTwisterRNG(1);
        for (DiscreteWFLOP problem : new DiscreteWFLOP[]{
                WFLOPCase.CASE_3.createDiscrete(WindScenario.condition1()),
                WFLOPCase.CASE_2.createDiscrete(WindScenario.condition1())}) {
            for (int trial = 0; trial < 50; trial++) {
                CellLayout layout = problem.randomLayouts().generate(problem, rng);
                assertTrue(problem.isFeasible(layout));
                for (int step = 0; step < 100; step++) {
                    MutationOperator<CellLayout> move = problem.neighborhood().get(rng.randInt(problem.neighborhood().size()));
                    CellLayout next = move.apply(layout, rng);
                    assertTrue(problem.isFeasible(next), move + " broke " + problem);
                    if (problem.isFixedCount())
                        assertEquals(layout.size(), next.size());
                    layout = next;
                }
            }
        }
    }

    @Test
    void addAndRemoveRespectTheLimits() {
        RNG rng = new MersenneTwisterRNG(2);
        CellLayout three = new CellLayout(10, new int[]{0, 50, 99});
        assertEquals(3, new AddTurbine(3).apply(three, rng).size());
        assertEquals(4, new AddTurbine(4).apply(three, rng).size());
        assertEquals(3, new RemoveTurbine(3).apply(three, rng).size());
        assertEquals(2, new RemoveTurbine(1).apply(three, rng).size());
    }

    @Test
    void neighborMoveStaysAdjacent() {
        RNG rng = new MersenneTwisterRNG(3);
        CellLayout single = new CellLayout(10, new int[]{55});    // row 5, column 5
        for (int k = 0; k < 50; k++) {
            int to = new MoveTurbineToNeighbor().apply(single, rng).cell(0);
            assertTrue(Math.abs(to / 10 - 5) <= 1 && Math.abs(to % 10 - 5) <= 1 && to != 55, "moved to " + to);
        }
    }

    @Test
    void crossoverKeepsSharedCellsAndTheCount() {
        RNG rng = new MersenneTwisterRNG(4);
        DiscreteWFLOP fixed = WFLOPCase.CASE_4.createDiscrete(WindScenario.condition1());
        DiscreteWFLOP variable = WFLOPCase.CASE_1.createDiscrete(WindScenario.condition1());
        CrossOverOperator<CellLayout> fixedCrossover = fixed.crossover();
        CrossOverOperator<CellLayout> variableCrossover = variable.crossover();
        for (int trial = 0; trial < 200; trial++) {
            CellLayout a = fixed.randomLayouts().generate(fixed, rng), b = fixed.randomLayouts().generate(fixed, rng);
            CellLayout child = fixedCrossover.apply(a, b, rng).get(0);
            assertEquals(39, child.size());
            for (int c : a.cells())
                if (b.contains(c))
                    assertTrue(child.contains(c), "shared cell " + c + " lost");
            Set<Integer> union = new HashSet<>();
            for (int c : a.cells())
                union.add(c);
            for (int c : b.cells())
                union.add(c);
            for (int c : child.cells())
                assertTrue(union.contains(c), "cell " + c + " comes from neither parent");

            CellLayout x = variable.randomLayouts().generate(variable, rng), y = variable.randomLayouts().generate(variable, rng);
            CellLayout mix = variableCrossover.apply(x, y, rng).get(0);
            assertTrue(mix.size() >= Math.min(x.size(), y.size()) && mix.size() <= Math.max(x.size(), y.size()));
        }
    }

    // Neighbors are evaluated incrementally from their parent: the result must be identical to a full evaluation
    @Test
    void incrementalEvaluationEqualsFullEvaluation() {
        RNG rng = new MersenneTwisterRNG(6);
        for (WFLOPCase c : new WFLOPCase[]{WFLOPCase.CASE_1, WFLOPCase.CASE_2, WFLOPCase.CASE_4})
            for (WindScenario wind : new WindScenario[]{WindScenario.condition1(), WindScenario.condition2()}) {
                DiscreteWFLOP problem = c.createDiscrete(wind);
                WindFarm farm = problem.farm();
                CellLayout current = problem.randomLayouts().generate(problem, rng);
                double currentCost = problem.cost(current);
                for (int step = 0; step < 2000; step++) {
                    MutationOperator<CellLayout> move = problem.neighborhood().get(rng.randInt(problem.neighborhood().size()));
                    CellLayout next = move.apply(current, rng);
                    if (step % 7 == 0)
                        next = move.apply(next, rng);             // two changes before the evaluation
                    double cost = problem.cost(next);
                    assertEquals(WindFarm.cost(next.size()) / farm.power(next.cells()), cost, c + " " + wind + " step " + step);
                    if (cost <= currentCost || rng.randDouble() < 0.2) {  // a walk that also goes uphill
                        current = next;
                        currentCost = cost;
                    }
                }
            }
    }

    @Test
    void evaluationCacheDoesNotChangeTheValue() {
        DiscreteWFLOP problem = WFLOPCase.CASE_1.createDiscrete(WindScenario.condition1());
        CellLayout a = new CellLayout(10, new int[]{3, 5, 7, 42});
        problem.cost(a);
        CellLayout b = a.withMoved(42, 43);
        CellLayout fresh = new CellLayout(10, new int[]{3, 5, 7, 43});
        assertEquals(fresh, b);
        assertEquals(fresh.hashCode(), b.hashCode());
        assertEquals(problem.cost(fresh), problem.cost(b));
        // a layout evaluated by one farm is evaluated again by another
        DiscreteWFLOP other = WFLOPCase.CASE_2.createDiscrete(WindScenario.condition2());
        assertEquals(other.cost(fresh), other.cost(b));
        assertNotEquals(problem.cost(b), other.cost(b));
        assertThrows(IllegalArgumentException.class, () -> a.withMoved(42, 3));
        assertThrows(IllegalArgumentException.class, () -> a.withAdded(100));
    }

    @Test
    void layoutsAreImmutableValues() {
        CellLayout a = new CellLayout(10, new int[]{7, 3, 5});
        assertArrayEquals(new int[]{3, 5, 7}, a.cells());
        CellLayout b = a.withMoved(5, 6);
        assertArrayEquals(new int[]{3, 5, 7}, a.cells());
        assertArrayEquals(new int[]{3, 6, 7}, b.cells());
        assertEquals(new CellLayout(10, new int[]{6, 7, 3}), b);
        assertEquals(b.hashCode(), new CellLayout(10, new int[]{3, 6, 7}).hashCode());
        assertThrows(IllegalArgumentException.class, () -> a.withAdded(3));
        assertThrows(IllegalArgumentException.class, () -> new CellLayout(10, new int[]{1, 1}));
    }
}
