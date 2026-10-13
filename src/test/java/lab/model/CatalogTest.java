package lab.model;

import metaheuristic.run.RunResult;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every algorithm the lab offers must run on its problem with the default parameters and produce a solution that
 * its visualizer can show.
 */
class CatalogTest {

    @Test
    void everyAlgorithmRunsAndCanBeVisualized() {
        List<String> failures = new ArrayList<>();
        for (LabProblem problem : Catalog.problems()) {
            LabProblem.Session session = problem.newSession(ParamValues.defaults(problem.params()));
            for (String algorithm : problem.algorithmNames()) {
                String name = problem.name() + " / " + algorithm;
                try {
                    LabProblem.StartedRun run = session.start(algorithm, ParamValues.defaults(problem.algorithmParams(algorithm)),
                            1, new EvaluationBasedTC(300), new SearchListener() {
                            }, Runnable::run);           // runs synchronously
                    RunResult<?> result = run.handle().result().join();
                    assertTrue(result.evaluations() > 0 && result.evaluations() <= 300, name + " evaluations");
                    assertTrue(Double.isFinite(result.bestCost()), name + " cost");
                    assertTrue(run.hasSolution(), name + " has no solution");
                    assertNotNull(run.viewBestSolution(), name + " view");
                } catch (RuntimeException | AssertionError e) {
                    failures.add(name + ": " + e);
                }
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    @Test
    void windFarmOffersBothEncodings() {
        LabProblem wflop = Catalog.problems().stream().filter(p -> p.name().contains("WFLOP")).findFirst().orElseThrow(AssertionError::new);
        assertTrue(wflop.algorithmNames().containsAll(Arrays.asList("DE", "PSO", "EA", "SA", "Local Search", "DQN")));
    }
}
