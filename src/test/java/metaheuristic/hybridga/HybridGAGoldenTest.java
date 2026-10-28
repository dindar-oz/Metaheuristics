package metaheuristic.hybridga;

import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.run.SearchListener;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.RouteCrossover;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden master: fixed runs must give exactly the recorded results, so that a change of the code that should not
 * change the search (a refactoring, a speed-up) can be checked bit for bit. The fingerprint covers the best cost, the
 * best solution, the evaluations and every generation's metrics, with diagnostics on (diversity and duplicates of the
 * population). The cases cover the reduced GA, the full research GA with frequent stagnation responses, every
 * mechanism switched off or on alone, the plain GA, other operators and a problem that needs every bed. If a change is
 * meant to alter the search, record the new fingerprints (the failure message lists them all).
 */
class HybridGAGoldenTest {

    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("reduced", "cost=40cb4205dab57868 evals=8000 iters=101 best=a810a823 trace=8f2ced98b27391ae"),
            Map.entry("reduced-ls30", "cost=40cbe69fceab033a evals=8000 iters=46 best=385e6aa7 trace=dc6efe9a3ceca912"),
            Map.entry("research-stagnation5", "cost=40cbfc88dd822a7c evals=6000 iters=34 best=3ad8b706 trace=8a454b604b4ffd89"),
            Map.entry("research-no-immigration", "cost=40cc13188207207e evals=5000 iters=28 best=122f1b8a trace=218479b8a9df3903"),
            Map.entry("reduced-annealing", "cost=40cbab5a40808cc4 evals=6000 iters=32 best=42956035 trace=70c6277c1f089d07"),
            Map.entry("plain-move-trip", "cost=40cc90f3cb0c9a58 evals=5000 iters=557 best=2efb6860 trace=4f9a607f82e22f9b"),
            Map.entry("route-crossover-age0", "cost=40ca7e5f5ed80820 evals=4000 iters=76 best=7dae1198 trace=b25cf352b78d0c75"),
            Map.entry("full-research", "cost=40c9910ca752ec31 evals=4000 iters=24 best=db6d1bc2 trace=1290a0481d91fd1b")
    );

    private record Case(Supplier<HybridGA.Builder<PECTOSolution>> ga, PECTOProblem problem, long seed, int evaluations) {
    }

    private static Map<String, Case> cases() {
        PECTOProblem medium = PECTOInstances.random(12, 150, 6, 30, 3);
        PECTOProblem full = PECTOInstances.random(8, 90, 3, 30, 2);
        Map<String, Case> c = new LinkedHashMap<>();
        c.put("reduced", new Case(() -> PECTOHybridGA.builder(medium), medium, 8, 8000));
        c.put("reduced-ls30", new Case(() -> PECTOHybridGA.builder(medium).localSearchSteps(30), medium, 2, 8000));
        c.put("research-stagnation5", new Case(() -> PECTOHybridGA.researchBuilder(medium).stagnationPeriod(5),
                medium, 7, 6000));
        c.put("research-no-immigration", new Case(() -> PECTOHybridGA.researchBuilder(medium).stagnationPeriod(3)
                .immigration(false).perturbationOnStagnation(false), medium, 4, 5000));
        c.put("reduced-annealing", new Case(() -> PECTOHybridGA.builder(medium)
                .annealing(25, 0.1, new GeometricCooling(0.95), 1), medium, 5, 6000));
        c.put("plain-move-trip", new Case(() -> PECTOHybridGA.builder(medium).hybrid(false).populationSize(20)
                .mutation(new MoveTrip()).mutationRate(0.2), medium, 5, 5000));
        c.put("route-crossover-age0", new Case(() -> PECTOHybridGA.builder(full).crossover(new RouteCrossover())
                .minVictimAge(0).populationSize(16), full, 9, 4000));
        c.put("full-research", new Case(() -> PECTOHybridGA.researchBuilder(full).stagnationPeriod(4), full, 1, 4000));
        return c;
    }

    @Test
    void runsAreBitForBitTheRecordedOnes() {
        Map<String, String> actual = new LinkedHashMap<>();
        cases().forEach((name, c) -> actual.put(name, fingerprint(c)));
        StringBuilder record = new StringBuilder();
        actual.forEach((name, f) -> record.append("            Map.entry(\"").append(name).append("\", \"").append(f).append("\"),\n"));
        assertEquals(EXPECTED, actual, "fingerprints of this code:\n" + record);
    }

    private static String fingerprint(Case c) {
        long[] trace = {17};
        SearchListener listener = new SearchListener() {
            @Override
            public void onProgress(ProgressSnapshot p) {
                trace[0] = mix(trace[0], p.evaluations());
                trace[0] = mix(trace[0], Double.doubleToLongBits(p.bestCost()));
                p.metrics().forEach((k, v) -> {
                    trace[0] = mix(trace[0], k.hashCode());
                    trace[0] = mix(trace[0], Double.doubleToLongBits(v));
                });
            }
        };
        RunResult<PECTOSolution> r = Runner.of(c.ga().get().build()).seed(c.seed()).diagnostics(true)
                .stopWhen(new EvaluationBasedTC(c.evaluations())).listener(listener).run(c.problem());
        return String.format("cost=%016x evals=%d iters=%d best=%08x trace=%016x",
                Double.doubleToLongBits(r.bestCost()), r.evaluations(), r.iterations(),
                Arrays.hashCode(r.bestSolution().toSequence()), trace[0]);
    }

    private static long mix(long h, long x) {
        h ^= x;
        h *= 0x9E3779B97F4A7C15L;
        return h ^ (h >>> 29);
    }
}
