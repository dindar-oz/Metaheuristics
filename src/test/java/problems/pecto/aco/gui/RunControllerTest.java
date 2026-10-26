package problems.pecto.aco.gui;

import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stepping back must show exactly what stepping forward showed: the run is replayed from checkpoints, and every replay
 * is the same run.
 */
class RunControllerTest {

    private static final PECTOProblem PROBLEM = PECTOInstances.random(4, 20, 3, 8, 3);
    private static final ColonySettings SETTINGS = new ColonySettings(4, 1, 2, 0.1, 0.05, 8, 3, 2, 10, true, 0, 1, 0.3, 0.5, 0.05);

    // what a frame shows, comparable across replays
    private static String signature(Frame f) {
        StringBuilder sb = new StringBuilder().append(f.seq()).append(' ').append(f.type()).append(" it=").append(f.iteration())
                .append(" ant=").append(f.ant()).append(" step=").append(f.step()).append(" best=").append(f.bestCost())
                .append(" evals=").append(f.evaluations()).append(" ants=").append(f.ants().size())
                .append(" tau=").append(Arrays.deepHashCode(f.pheromone().patient())).append('/')
                .append(Arrays.deepHashCode(f.pheromone().hospital()));
        if (f.construction() != null)
            sb.append(" stops=").append(Arrays.deepHashCode(f.construction().stops())).append(" room=")
                    .append(Arrays.toString(f.construction().room())).append(" states=")
                    .append(Arrays.toString(f.construction().rateState()));
        Object d = f.decision();
        if (d instanceof Frame.AmbulanceDecision a)
            sb.append(" ambulance ").append(a.chosen()).append(" of ").append(Arrays.toString(a.shortlist()));
        else if (d instanceof Frame.PatientDecision p)
            sb.append(" patient ").append(p.chosen()).append(" u=").append(p.u());
        else if (d instanceof Frame.HospitalDecision h)
            sb.append(" hospital ").append(h.chosen()).append(" u=").append(h.u());
        return sb.toString();
    }

    private static Frame await(RunController c) throws InterruptedException {
        assertTrue(c.awaitStop(30_000), "the run did not stop");
        assertEquals(RunController.Status.PAUSED, c.status(), c.failure());
        return c.latest();
    }

    @Test
    void steppingBackShowsWhatSteppingForwardShowed() throws InterruptedException {
        RunController c = new RunController(PROBLEM, SETTINGS, 7);
        try {
            c.start();
            Frame first = await(c);
            assertEquals(EventType.START, first.type());
            assertEquals(1, first.seq());

            c.setLevel(EventType.Level.DECISION);
            List<String> forward = new ArrayList<>(List.of(signature(first)));
            for (int i = 0; i < 60; i++) {
                c.step();
                forward.add(signature(await(c)));
            }
            for (int i = 0; i < 25; i++) {
                c.back();
                assertEquals(forward.get(forward.size() - 2 - i), signature(await(c)), "back " + (i + 1));
            }
        } finally {
            c.dispose();
        }
    }

    @Test
    void jumpsBackAcrossIterationsGiveTheSameRun() throws InterruptedException {
        RunController c = new RunController(PROBLEM, SETTINGS, 11);
        try {
            c.start();
            await(c);
            c.setLevel(EventType.Level.ITERATION);
            List<String> ends = new ArrayList<>();
            for (int i = 0; i <= 8; i++) {
                c.step();
                Frame f = await(c);
                assertEquals(EventType.ITERATION_DONE, f.type());
                assertEquals(i, f.iteration());
                ends.add(signature(f));
            }
            for (int target : new int[]{2, 7, 0, 5}) {
                c.runToIteration(target);
                assertEquals(ends.get(target), signature(await(c)), "iteration " + target);
            }
            // back at the step level from the end of an iteration: the coarser events too, then the last ant's last step
            c.runToIteration(3);
            await(c);
            c.setLevel(EventType.Level.STEP);
            for (EventType expected : List.of(EventType.PHEROMONE_UPDATED, EventType.IMPROVED, EventType.ANT_DONE,
                    EventType.STEP_DONE)) {
                c.back();
                Frame f = await(c);
                assertEquals(expected, f.type());
                assertEquals(3, f.iteration());
            }
            Frame f = c.latest();
            assertEquals(SETTINGS.ants() - 1, f.ant());
            assertEquals(PROBLEM.patientCount(), f.step());
            c.restart();
            assertEquals(1, await(c).seq());
        } finally {
            c.dispose();
        }
    }
}
