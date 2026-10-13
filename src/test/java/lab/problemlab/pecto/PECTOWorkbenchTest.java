package lab.problemlab.pecto;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import lab.model.ParamValues;
import lab.problemlab.ProblemLabCatalog;
import lab.problemlab.ProblemWorkbench;
import lab.problemlab.model.ComponentSpec;
import lab.problemlab.model.Evaluation;
import lab.problemlab.model.Workspace;
import lab.problemlab.ui.ViewHost;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Urgency;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every component the PECTO workbench offers works with its default parameters, files round-trip, and the editor
 * turns its gestures into undoable edits.
 */
class PECTOWorkbenchTest {

    private final PECTOWorkbench workbench = new PECTOWorkbench();

    @Test
    void theCatalogOffersPecto() {
        assertTrue(ProblemLabCatalog.workbenches().stream().anyMatch(w -> w instanceof PECTOWorkbench));
    }

    @Test
    void everyComponentWorksWithItsDefaults() {
        RNG rng = new MersenneTwisterRNG(1);
        for (ComponentSpec<Void, PECTOProblem> instance : workbench.instanceGenerators()) {
            PECTOProblem problem = instance.create(null, ParamValues.defaults(instance.params()));
            for (ComponentSpec<PECTOProblem, InitialSolutionGenerator<PECTOSolution>> generator : workbench.solutionGenerators()) {
                PECTOSolution solution = generator.create(problem, ParamValues.defaults(generator.params())).generate(problem, rng);
                Evaluation e = workbench.evaluate(problem, solution);
                assertEquals(problem.cost(solution), e.cost(), instance + " / " + generator);
                assertEquals(problem.isFeasible(solution), e.feasible());
                assertEquals(e.feasible(), e.violations().isEmpty(), instance + " / " + generator + ": " + e.violations());
                for (ComponentSpec<PECTOProblem, MutationOperator<PECTOSolution>> mutation : workbench.mutations()) {
                    PECTOSolution next = mutation.create(problem, ParamValues.defaults(mutation.params())).apply(solution, rng);
                    assertEquals(problem.isFeasible(solution), problem.isFeasible(next), mutation + " changed feasibility");
                }
            }
        }
    }

    @Test
    void filesRoundTrip(@org.junit.jupiter.api.io.TempDir Path dir) throws Exception {
        ComponentSpec<Void, PECTOProblem> random = workbench.instanceGenerators().get(0);
        PECTOProblem problem = random.create(null, ParamValues.defaults(random.params()));
        PECTOSolution solution = workbench.solutionGenerators().get(0).create(problem, null).generate(problem, null);
        Path instanceFile = dir.resolve("a.pecto"), solutionFile = dir.resolve("a.pectosol");
        workbench.instanceFormat().orElseThrow().write(null, problem, instanceFile);
        workbench.solutionFormat().orElseThrow().write(problem, solution, solutionFile);
        assertTrue(Files.readString(instanceFile).contains("PATIENTS " + problem.patientCount()));
        PECTOProblem read = workbench.instanceFormat().orElseThrow().read(null, instanceFile);
        PECTOSolution readSolution = workbench.solutionFormat().orElseThrow().read(read, solutionFile);
        assertEquals(problem.cost(solution), read.cost(readSolution));
    }

    @Test
    void editorGesturesBecomeUndoableEdits() {
        Workspace<PECTOSolution, PECTOProblem> workspace = new Workspace<>(workbench::evaluate);
        FakeHost host = new FakeHost();
        PECTOEditor editor = (PECTOEditor) workbench.createView(workspace, host);
        workspace.addListener(editor::stateChanged);

        ComponentSpec<Void, PECTOProblem> blank = workbench.instanceGenerators().get(2);
        workspace.newInstance(blank.create(null, ParamValues.defaults(blank.params())), "blank");     // ambulance 0, hospital 1
        workspace.setSolution(workspace.problem().emptySolution(), "empty");

        editor.setTool(PECTOEditor.Tool.PATIENT);
        editor.addParticipant(PECTOEditor.Tool.PATIENT, 100, 100);            // patient 1, hospital now 2
        PECTOProblem problem = workspace.problem();
        assertEquals(1, problem.patientCount());
        assertEquals(1, editor.selected());
        assertEquals(Urgency.MODERATE, problem.urgency(1));
        assertFalse(workspace.state().evaluation().feasible());               // nobody transports the new patient

        editor.link(1, 2);                                                     // drag the patient onto the hospital
        assertTrue(workspace.state().evaluation().feasible());
        assertEquals(0, editor.route());                                       // the route follows the patient

        double cost = workspace.state().evaluation().cost();
        editor.setUrgency(1, Urgency.CRITICAL);
        assertEquals(cost * Urgency.CRITICAL.weight() / Urgency.MODERATE.weight(), workspace.state().evaluation().cost(), 1e-9);

        editor.nudge(1, 0);
        editor.nudge(1, 0);                                                    // merged into one history entry
        assertEquals(102, workspace.problem().x(1));
        int entries = workspace.history().size();

        editor.deleteParticipant(2);                                           // the last hospital cannot go
        assertEquals(1, host.errors.size());
        assertEquals(entries, workspace.history().size());

        editor.unassign(1);
        assertFalse(workspace.state().evaluation().feasible());
        workspace.undo();
        assertTrue(workspace.state().evaluation().feasible());
        assertEquals(1, editor.selected());
    }

    private static final class FakeHost implements ViewHost {
        final List<String> errors = new ArrayList<>();

        @Override
        public void status(String message) {
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }

        @Override
        public RNG rng() {
            return new MersenneTwisterRNG(1);
        }

        @Override
        public Window window() {
            return null;
        }
    }

    // the workbench is usable through the generic interface alone
    @SuppressWarnings("unused")
    private static ProblemWorkbench<PECTOSolution, PECTOProblem> generic() {
        return new PECTOWorkbench();
    }
}
