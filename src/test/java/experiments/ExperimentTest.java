package experiments;

import metaheuristic.run.RunResult;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import testsupport.Fixtures;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExperimentTest {

    private static List<String> run(int threads) {
        List<String> rows = new ArrayList<>();
        Experiment.of(Fixtures.allAlgorithms())
                .repeats(3)
                .seed(99)
                .stopWhen(new EvaluationBasedTC(1500))
                .threads(threads)
                .handler((c, runs) -> {
                    for (RunResult<?> r : runs)
                        rows.add(c.getTitle() + " " + r.seed() + " " + r.bestCost() + " " + r.evaluations() + " " + r.stopReason());
                })
                .run();
        return rows;
    }

    @Test
    void resultsDoNotDependOnThreadCount() {
        assertEquals(run(1), run(6));
    }
}
