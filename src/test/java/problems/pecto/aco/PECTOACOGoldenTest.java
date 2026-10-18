package problems.pecto.aco;

import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Golden master: fixed runs must give exactly the recorded results, so that a change of the code that should not
 * change the search (a refactoring, a speed-up) can be checked bit for bit. The fingerprint covers the best cost, the
 * best solution, the evaluations and every iteration's metrics, with diagnostics on (diversity and duplicates over all
 * ants). The cases cover every branch of the heuristic exponent, explorers, the tau_min floor, both execution modes,
 * no local search, scarce beds, tiny instances and a Table I instance. If a change is meant to alter the search,
 * record the new fingerprints (the failure message lists them all).
 */
class PECTOACOGoldenTest {

    private static final Map<String, String> EXPECTED = Map.ofEntries(
            Map.entry("defaults", "cost=40c5437bdbfefb0a evals=3000 iters=27 best=35bf23bb trace=56e63bec6a8faff7"),
            Map.entry("plain", "cost=40c57a7974cd7e67 evals=3000 iters=27 best=ea91c179 trace=4c4787321d24401e"),
            Map.entry("explorers-beta0-floor", "cost=40c5985356382991 evals=2500 iters=23 best=b793a962 trace=76db757302c35b07"),
            Map.entry("explorers-synchronous", "cost=40c5985356382991 evals=2500 iters=23 best=b793a962 trace=76db757302c35b07"),
            Map.entry("pow-alpha2-rate1", "cost=40c5b6f477c64fe8 evals=2000 iters=18 best=85a4f908 trace=85f720dd19ae6777"),
            Map.entry("beta1-no-local-search", "cost=40c5fbd030c74c20 evals=800 iters=99 best=bee35a8f trace=1c5fa6e74bba87c2"),
            Map.entry("half-exponents", "cost=40c66561a062c961 evals=1500 iters=14 best=70dc3151 trace=44946a3582c9b022"),
            Map.entry("scarce-beds", "cost=40ca4ab0f09f452e evals=1200 iters=11 best=93efbe62 trace=9c16b8d70c03c101"),
            Map.entry("tiny-1", "cost=40629ab759412c78 evals=600 iters=4 best=00007480 trace=12f120d3fbe128be"),
            Map.entry("tiny-2", "cost=4080be01423578e4 evals=600 iters=4 best=6d46a8a3 trace=f3e10ae009bad8ca"),
            Map.entry("tiny-0", "cost=0000000000000000 evals=1 iters=0 best=000003c2 trace=0000000000000011"),
            Map.entry("table-I-1", "cost=40f1010f03f02c39 evals=330 iters=3 best=da932025 trace=36b5c1280edb493f")
    );

    private record Case(Supplier<PECTOACO.Builder> aco, PECTOProblem problem, long seed, int evaluations) {
    }

    private static Map<String, Case> cases() {
        PECTOProblem medium = PECTOInstances.random(8, 120, 5, 30, 4);
        PECTOProblem scarce = PECTOInstances.random(8, 90, 3, 30, 2);
        Map<String, Case> c = new LinkedHashMap<>();
        c.put("defaults", new Case(() -> PECTOACO.builder().antCount(10), medium, 11, 3000));
        c.put("plain", new Case(() -> PECTOACO.builder().antCount(10).explorers(0), medium, 11, 3000));
        c.put("explorers-beta0-floor", new Case(() -> PECTOACO.builder().antCount(7).explorers(4).explorerRate(0.3)
                .explorerBeta(0).explorerDeposit(0.2).tauMinRatio(0.025), medium, 5, 2500));
        c.put("explorers-synchronous", new Case(() -> PECTOACO.builder().antCount(7).explorers(4).explorerRate(0.3)
                .explorerBeta(0).explorerDeposit(0.2).tauMinRatio(0.025)
                .executionMode(PECTOACO.ExecutionMode.SYNCHRONOUS).threads(3), medium, 5, 2500));
        c.put("pow-alpha2-rate1", new Case(() -> PECTOACO.builder().antCount(6).alpha(2).beta(1.5).explorers(2)
                .explorerRate(1).explorerBeta(2).explorerDeposit(1), medium, 3, 2000));
        c.put("beta1-no-local-search", new Case(() -> PECTOACO.builder().antCount(8).beta(1).explorerBeta(3)
                .localSearchSteps(0).optimalHospitals(false).shortlist(1), medium, 9, 800));
        c.put("half-exponents", new Case(() -> PECTOACO.builder().antCount(6).alpha(0.5).beta(0.5).rho(0.5).pBest(0.2)
                .explorers(6).explorerRate(0.5).explorerBeta(1), medium, 21, 1500));
        c.put("scarce-beds", new Case(() -> PECTOACO.builder().antCount(5).shortlist(8).candidates(5)
                .hospitalCandidates(2), scarce, 1, 1200));
        c.put("tiny-1", new Case(() -> PECTOACO.builder().candidates(50).shortlist(10),
                PECTOInstances.random(1, 1, 1, 1, 1), 1, 600));
        c.put("tiny-2", new Case(() -> PECTOACO.builder().candidates(50).shortlist(10),
                PECTOInstances.random(3, 2, 1, 2, 1), 1, 600));
        c.put("tiny-0", new Case(() -> PECTOACO.builder().candidates(50).shortlist(10),
                PECTOInstances.random(2, 0, 1, 0, 1), 1, 100));
        c.put("table-I-1", new Case(() -> PECTOACO.builder().antCount(4), PECTOInstances.paper(1, 42), 7, 330));
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
        RunResult<PECTOSolution> r = Runner.of(c.aco().get().build()).seed(c.seed()).diagnostics(true)
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
