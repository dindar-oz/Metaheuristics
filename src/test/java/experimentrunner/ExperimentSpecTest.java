package experimentrunner;

import lab.model.Catalog;
import org.junit.jupiter.api.Test;
import util.random.Seeds;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ExperimentSpecTest {

    // a small, fast experiment: SA with two cooling rates on two random TSPs
    public static ExperimentSpec tsp(String id, int repeats, long evaluations) {
        ExperimentSpec s = new ExperimentSpec();
        s.id = id;
        s.title = "SA cooling on small TSPs";
        s.question = "Does slower cooling help?";
        s.analysisPlan = "Paired gap to the fast cooling.";
        s.problem = "TSP (random Euclidean)";
        s.problemParams.put("cities", 40.0);           // JSON numbers arrive as doubles
        ExperimentSpec.Instance a = new ExperimentSpec.Instance(), b = new ExperimentSpec.Instance();
        a.label = "A";
        a.params.put("instance seed", 1.0);
        b.label = "B";
        b.seedKey = 7L;
        b.params.put("instance seed", 2.0);
        s.instances = List.of(a, b);
        s.algorithm = "SA";
        ExperimentSpec.Config fast = new ExperimentSpec.Config(), slow = new ExperimentSpec.Config();
        fast.label = "fast";
        fast.params.put("cooling rate", 0.99);
        slow.label = "slow";
        slow.params.put("cooling rate", 0.999);
        s.configs = List.of(fast, slow);
        s.baseline = "fast";
        s.seeds.base = 99;
        s.seeds.repeats = repeats;
        s.budget.evaluations = evaluations;
        s.traceEvery = 10;
        return s;
    }

    @Test
    void validatesAndExpandsRepeatMajor() {
        ExperimentSpec s = tsp("tsp-1", 3, 2000);
        s.validate(Catalog.problems());
        assertEquals(0L, s.instances.get(0).seedKey);      // its position
        List<RunKey> runs = s.runs();
        assertEquals(12, runs.size());
        assertEquals(new RunKey("fast", "A", 0), runs.get(0));
        assertEquals(new RunKey("slow", "B", 0), runs.get(3));
        assertEquals(new RunKey("fast", "A", 1), runs.get(4));
        assertEquals(Seeds.derive(99, 7, 2), s.seed(new RunKey("slow", "B", 2)));
        // the same seed in every configuration
        assertEquals(s.seed(new RunKey("fast", "B", 2)), s.seed(new RunKey("slow", "B", 2)));
    }

    @Test
    void resolvesParametersOverDefaults() {
        ExperimentSpec s = tsp("tsp-2", 1, 2000);
        s.validate(Catalog.problems());
        var p = s.labProblem(Catalog.problems());
        var v = s.problemValues(p, s.instance("B"));
        assertEquals(40, v.getInt("cities"));
        assertEquals(2, v.getInt("instance seed"));
        var a = s.algorithmValues(p, s.config("slow"));
        assertEquals(0.999, a.getDouble("cooling rate"));
        assertEquals(20, a.getInt("inner loop"));          // the lab's default
    }

    @Test
    void reportsEveryProblem() {
        ExperimentSpec s = tsp("bad id!", 0, 0);
        s.configs.get(1).params.put("colling rate", 0.5);
        s.configs.get(0).params.put("inner loop", 2.5);
        s.baseline = "none";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> s.validate(Catalog.problems()));
        for (String part : new String[]{"id must", "unknown parameter 'colling rate'", "inner loop", "baseline 'none'",
                "seeds.repeats", "budget"})
            assertTrue(e.getMessage().contains(part), part + " in " + e.getMessage());
        ExperimentSpec unknown = tsp("x", 1, 10);
        unknown.algorithm = "Gradient descent";
        assertThrows(IllegalArgumentException.class, () -> unknown.validate(Catalog.problems()));
        ExperimentSpec twice = tsp("y", 1, 10);
        twice.configs.get(1).label = "fast";
        assertThrows(IllegalArgumentException.class, () -> twice.validate(Catalog.problems()));
    }

    @Test
    void roundTripsThroughJson() {
        ExperimentSpec s = tsp("tsp-3", 2, 500);
        ExperimentSpec back = Json.GSON.fromJson(Json.GSON.toJson(s), ExperimentSpec.class);
        back.validate(Catalog.problems());
        assertEquals(s.runs(), back.runs());
        assertEquals(Map.of("cooling rate", 0.999), back.config("slow").params);
    }
}
