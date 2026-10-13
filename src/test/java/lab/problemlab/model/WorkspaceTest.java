package lab.problemlab.model;

import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.RandomPECTOSG;
import util.random.MersenneTwisterRNG;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkspaceTest {

    private static final PECTOProblem PROBLEM = PECTOInstances.random(5, 40, 3, 20, 1);

    private static Workspace<PECTOSolution, PECTOProblem> workspace() {
        return new Workspace<>(Evaluation::of);
    }

    @Test
    void undoRedoAndBranching() {
        Workspace<PECTOSolution, PECTOProblem> w = workspace();
        assertFalse(w.hasInstance());
        w.newInstance(PROBLEM, "instance");
        PECTOSolution greedy = new GreedyPECTOSG(PROBLEM).generate(PROBLEM, null);
        w.setSolution(greedy, "greedy");
        assertEquals(PROBLEM.cost(greedy), w.state().evaluation().cost());
        assertTrue(w.state().evaluation().feasible());

        w.undo();
        assertNull(w.solution());
        assertTrue(w.canRedo());
        w.redo();
        assertSame(greedy, w.solution());

        w.undo();
        w.setSolution(PROBLEM.emptySolution(), "empty");       // drops the redo branch
        assertFalse(w.canRedo());
        assertEquals(List.of("instance", "empty"), w.history().stream().map(LabState::action).toList());
        assertFalse(w.state().evaluation().feasible());

        w.goTo(0);
        assertEquals(0, w.currentIndex());
        assertThrows(IndexOutOfBoundsException.class, () -> w.goTo(5));
        assertThrows(IllegalStateException.class, () -> workspace().setSolution(greedy, "no instance"));
    }

    @Test
    void changesWithTheSameMergeKeyShareAnEntry() {
        Workspace<PECTOSolution, PECTOProblem> w = workspace();
        w.newInstance(PROBLEM, "instance");
        PECTOSolution s = new RandomPECTOSG(PROBLEM).generate(PROBLEM, new MersenneTwisterRNG(1));
        Object key = new Object();
        w.change(PROBLEM, s, "step 1", key);
        w.change(PROBLEM, s, "step 2", key);
        assertEquals(2, w.history().size());
        assertEquals("step 2", w.state().action());
        w.change(PROBLEM, s, "other", null);
        w.change(PROBLEM, s, "step 3", key);                     // the key no longer matches the newest entry
        assertEquals(4, w.history().size());
    }

    @Test
    void listenersSeeThePreviousAndTheCurrentState() {
        Workspace<PECTOSolution, PECTOProblem> w = workspace();
        List<String> seen = new ArrayList<>();
        w.addListener((previous, current) -> seen.add((previous == null ? "-" : previous.action()) + ">" + current.action()));
        w.newInstance(PROBLEM, "a");
        w.setSolution(PROBLEM.emptySolution(), "b");
        w.undo();
        assertEquals(List.of("->a", "a>b", "b>a"), seen);
    }

    @Test
    void historyIsBounded() {
        Workspace<PECTOSolution, PECTOProblem> w = workspace();
        for (int k = 0; k < Workspace.MAX_HISTORY + 20; k++)
            w.newInstance(PROBLEM, "step " + k);
        assertEquals(Workspace.MAX_HISTORY, w.history().size());
        assertEquals("step " + (Workspace.MAX_HISTORY + 19), w.state().action());
    }

    @Test
    void hillClimbingNeverGetsWorse() {
        PECTOSolution start = new RandomPECTOSG(PROBLEM).generate(PROBLEM, new MersenneTwisterRNG(2));
        MutationStep<PECTOSolution> step = MutationStep.run(PROBLEM, start, new MoveTrip(), 500,
                MutationStep.Acceptance.BETTER, new MersenneTwisterRNG(3));
        assertTrue(PROBLEM.cost(step.solution()) < PROBLEM.cost(start));
        assertTrue(step.accepted() > 0 && step.accepted() <= step.changed() && step.changed() <= step.tried());
        MutationStep<PECTOSolution> walk = MutationStep.run(PROBLEM, start, new MoveTrip(), 50,
                MutationStep.Acceptance.ALWAYS, new MersenneTwisterRNG(3));
        assertEquals(walk.changed(), walk.accepted());
    }
}
