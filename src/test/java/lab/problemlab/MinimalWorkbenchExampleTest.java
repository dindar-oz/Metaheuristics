package lab.problemlab;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.RandomPermutationSG;
import lab.model.ParamValues;
import lab.problemlab.model.ComponentSpec;
import lab.problemlab.model.MutationStep;
import lab.problemlab.model.Workspace;
import lab.problemlab.ui.ProblemView;
import lab.problemlab.ui.TextProblemView;
import lab.problemlab.ui.ViewHost;
import operators.RandomRemoveReinsert;
import operators.TwoOpt;
import org.junit.jupiter.api.Test;
import problems.tsp.TSPProblem;
import representation.IntegerPermutation;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.awt.*;
import java.util.List;

import static lab.model.ParamSpec.integer;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The minimal workbench of the developer guide (doc/Problem-Lab-Developer-Guide.md), compiled and run here so that
 * the guide's example stays correct: a problem supported with nothing but its components gets the text view and
 * everything the lab window offers around it.
 */
class MinimalWorkbenchExampleTest {

    // ---- the example of the guide, step 1

    static final class TSPWorkbench implements ProblemWorkbench<IntegerPermutation, TSPProblem> {

        @Override
        public String name() {
            return "Travelling salesman (TSP)";
        }

        @Override
        public String description() {
            return "Visit every city once and return to the start; minimize the length of the tour.";
        }

        @Override
        public List<ComponentSpec<Void, TSPProblem>> instanceGenerators() {
            return List.of(ComponentSpec.generator("Random (unit square)", "Cities placed uniformly in the unit square.",
                    List.of(integer("cities", 30, 3, 5000, "number of cities"),
                            integer("seed", 42, 0, Integer.MAX_VALUE, "seed of the coordinates")),
                    v -> TSPProblem.randomEuclidean(v.getInt("cities"), v.getInt("seed"))));
        }

        @Override
        public List<ComponentSpec<TSPProblem, InitialSolutionGenerator<IntegerPermutation>>> solutionGenerators() {
            return List.of(ComponentSpec.of("Random tour", "The cities in uniformly random order.",
                    problem -> new RandomPermutationSG(problem.getCityCount())));
        }

        @Override
        public List<ComponentSpec<TSPProblem, MutationOperator<IntegerPermutation>>> mutations() {
            return List.of(
                    ComponentSpec.of("2-opt", "Reverses a random part of the tour.", problem -> new TwoOpt()),
                    ComponentSpec.of("Remove & reinsert", "Moves a random city to a random position.",
                            problem -> new RandomRemoveReinsert<>()));
        }
    }

    // ---- checks

    @Test
    void aWorkbenchWithOnlyComponentsWorksInTheLab() {
        TSPWorkbench workbench = new TSPWorkbench();
        Workspace<IntegerPermutation, TSPProblem> workspace = new Workspace<>(workbench::evaluate);
        ProblemView<IntegerPermutation, TSPProblem> view = workbench.createView(workspace, new SilentHost());
        assertInstanceOf(TextProblemView.class, view);
        workspace.addListener(view::stateChanged);

        var instance = workbench.instanceGenerators().get(0);
        workspace.newInstance(instance.create(null, ParamValues.defaults(instance.params())), "instance");
        TSPProblem problem = workspace.problem();
        IntegerPermutation tour = workbench.solutionGenerators().get(0).create(problem, null).generate(problem, new MersenneTwisterRNG(1));
        workspace.setSolution(tour, "random tour");

        MutationStep<IntegerPermutation> climb = MutationStep.run(problem, tour, workbench.mutations().get(0).create(problem, null),
                2000, MutationStep.Acceptance.BETTER, new MersenneTwisterRNG(2));
        workspace.change(problem, climb.solution(), "2-opt hill climbing");
        assertTrue(workspace.state().evaluation().cost() < workspace.history().get(1).evaluation().cost());
        assertTrue(workbench.instanceFormat().isEmpty());          // no files: the lab disables open and save
        assertNotNull(view.component());
    }

    private static final class SilentHost implements ViewHost {
        @Override
        public void status(String message) {
        }

        @Override
        public void error(String message) {
            fail(message);
        }

        @Override
        public RNG rng() {
            return new MersenneTwisterRNG(0);
        }

        @Override
        public Window window() {
            return null;
        }
    }
}
