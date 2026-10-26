package problems.pecto.aco.gui;

import base.OptimizationProblem;
import metaheuristic.Metaheuristic;
import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.run.SearchListener;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.aco.PECTOACO;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The diagnosis must show PECTOACO as it runs: its copy of the algorithm must give exactly PECTOACO's runs, and
 * watching it (frames, checkpoints, replays) must not change them. If PECTOACO changes, change the copy in this
 * package the same way.
 */
class DiagnosticColonyTest {

    private record Case(ColonySettings settings, PECTOProblem problem, long seed, int evaluations) {
    }

    private static ColonySettings settings(int ants, double alpha, double beta, double rho, double pBest, int candidates,
                                           int hospitalCandidates, int shortlist, int localSearch, boolean optimal,
                                           double tauMinRatio, int explorers, double rate, double explorerBeta, double deposit) {
        return new ColonySettings(ants, alpha, beta, rho, pBest, candidates, hospitalCandidates, shortlist, localSearch,
                optimal, tauMinRatio, explorers, rate, explorerBeta, deposit);
    }

    // the cases of PECTOACOGoldenTest: every branch of the heuristic exponent, explorers, the tau_min floor, no local
    // search, scarce beds, tiny instances and a Table I instance
    private static Map<String, Case> cases() {
        PECTOProblem medium = PECTOInstances.random(8, 120, 5, 30, 4);
        PECTOProblem scarce = PECTOInstances.random(8, 90, 3, 30, 2);
        ColonySettings d = ColonySettings.defaults();
        Map<String, Case> c = new LinkedHashMap<>();
        c.put("defaults", new Case(settings(10, 1, 2, 0.1, 0.05, 20, 5, 3, 100, true, 0, 2, 0.05, 0.5, 0.05), medium, 11, 3000));
        c.put("plain", new Case(settings(10, 1, 2, 0.1, 0.05, 20, 5, 3, 100, true, 0, 0, 0.05, 0.5, 0.05), medium, 11, 3000));
        c.put("explorers-beta0-floor", new Case(settings(7, 1, 2, 0.1, 0.05, 20, 5, 3, 100, true, 0.025, 4, 0.3, 0, 0.2), medium, 5, 2500));
        c.put("pow-alpha2-rate1", new Case(settings(6, 2, 1.5, 0.1, 0.05, 20, 5, 3, 100, true, 0, 2, 1, 2, 1), medium, 3, 2000));
        c.put("beta1-no-local-search", new Case(settings(8, 1, 1, 0.1, 0.05, 20, 5, 1, 0, false, 0, 2, 0.05, 3, 0.05), medium, 9, 800));
        c.put("half-exponents", new Case(settings(6, 0.5, 0.5, 0.5, 0.2, 20, 5, 3, 100, true, 0, 6, 0.5, 1, 0.05), medium, 21, 1500));
        c.put("scarce-beds", new Case(settings(5, 1, 2, 0.1, 0.05, 5, 2, 8, 100, true, 0, 2, 0.05, 0.5, 0.05), scarce, 1, 1200));
        c.put("tiny-1", new Case(settings(20, 1, 2, 0.1, 0.05, 50, 5, 10, 100, true, 0, 2, 0.05, 0.5, 0.05), PECTOInstances.random(1, 1, 1, 1, 1), 1, 600));
        c.put("tiny-2", new Case(settings(20, 1, 2, 0.1, 0.05, 50, 5, 10, 100, true, 0, 2, 0.05, 0.5, 0.05), PECTOInstances.random(3, 2, 1, 2, 1), 1, 600));
        c.put("tiny-0", new Case(settings(20, 1, 2, 0.1, 0.05, 50, 5, 10, 100, true, 0, 2, 0.05, 0.5, 0.05), PECTOInstances.random(2, 0, 1, 0, 1), 1, 100));
        c.put("table-I-1", new Case(new ColonySettings(4, d.alpha(), d.beta(), d.rho(), d.pBest(), d.candidates(),
                d.hospitalCandidates(), d.shortlist(), d.localSearchSteps(), d.optimalHospitals(), d.tauMinRatio(),
                d.explorers(), d.explorerRate(), d.explorerBeta(), d.explorerDeposit()), PECTOInstances.paper(1, 42), 7, 330));
        return c;
    }

    // what a run did: the best cost, solution and evaluations, and every iteration's metrics
    private record Trace(double best, PECTOSolution solution, long evaluations, long iterations,
                         List<Map<String, Double>> metrics) {
    }

    private static <P extends OptimizationProblem<PECTOSolution>> Trace trace(Metaheuristic<PECTOSolution, P> algorithm,
                                                                              P problem, long seed, int evaluations) {
        List<Map<String, Double>> metrics = new ArrayList<>();
        RunResult<PECTOSolution> r = Runner.of(algorithm).seed(seed).diagnostics(true)
                .stopWhen(new EvaluationBasedTC(evaluations)).listener(new SearchListener() {
                    @Override
                    public void onProgress(ProgressSnapshot progress) {
                        metrics.add(progress.metrics());
                    }
                }).run(problem);
        return new Trace(r.bestCost(), r.bestSolution(), r.evaluations(), r.iterations(), metrics);
    }

    @Test
    void sameRunsAsPECTOACO() {
        cases().forEach((name, c) -> {
            Trace expected = trace(c.settings().toPECTOACO(), c.problem(), c.seed(), c.evaluations());
            Trace actual = trace(new DiagnosticColony(c.settings()), c.problem(), c.seed(), c.evaluations());
            assertEquals(expected, actual, name);
        });
    }

    @Test
    void sameRunsAsPECTOACOBuildingAntsInParallel() {
        Case c = cases().get("explorers-beta0-floor");
        PECTOACO parallel = PECTOACO.builder().antCount(7).explorers(4).explorerRate(0.3).explorerBeta(0).explorerDeposit(0.2)
                .tauMinRatio(0.025).executionMode(PECTOACO.ExecutionMode.SYNCHRONOUS).threads(3).build();
        assertEquals(trace(parallel, c.problem(), c.seed(), c.evaluations()),
                trace(new DiagnosticColony(c.settings()), c.problem(), c.seed(), c.evaluations()));
    }

    @Test
    void defaultsArePECTOACOs() {
        assertEquals(PECTOACO.builder().build().describe(), ColonySettings.defaults().toPECTOACO().describe());
    }

    // a run on its own context, as the GUI runs it: best cost and evaluations per iteration
    private static List<String> diagnosed(Case c, Probe probe) {
        ColonyContext ctx = new ColonyContext(c.problem(), c.seed(), c.evaluations());
        List<String> out = new ArrayList<>();
        ColonyRun run = new ColonyRun(c.settings(), c.problem(), new Candidates(c.problem(), c.settings().candidates()), ctx,
                new Probe() {
                    @Override
                    public boolean frameAt(EventType type, long seq, int iteration) {
                        return probe.frameAt(type, seq, iteration);
                    }

                    @Override
                    public void deliver(Frame frame) {
                        probe.deliver(frame);
                    }

                    @Override
                    public void iterationStarts(int iteration, long seq, Supplier<Checkpoint> checkpoint) {
                        out.add(iteration + ": best=" + ctx.bestCost() + " evals=" + ctx.evaluations() + " " + ctx.metrics());
                        probe.iterationStarts(iteration, seq, checkpoint);
                    }
                });
        run.run();
        out.add("end: best=" + ctx.bestCost() + " evals=" + ctx.evaluations() + " " + ctx.metrics());
        return out;
    }

    @Test
    void ownContextGivesTheRunnersRun() {
        Case c = cases().get("defaults");
        Trace runner = trace(new DiagnosticColony(c.settings()), c.problem(), c.seed(), c.evaluations());
        List<String> own = diagnosed(c, Probe.NONE);
        assertEquals("end: best=" + runner.best() + " evals=" + runner.evaluations() + " "
                + runner.metrics().get(runner.metrics().size() - 1), own.get(own.size() - 1));
    }

    @Test
    void watchingEveryEventChangesNothing() {
        for (String name : List.of("defaults", "explorers-beta0-floor", "scarce-beds", "pow-alpha2-rate1")) {
            Case c = cases().get(name);
            List<Frame> frames = new ArrayList<>();
            Probe everything = new Probe() {
                @Override
                public boolean frameAt(EventType type, long seq, int iteration) {
                    return true;
                }

                @Override
                public void deliver(Frame frame) {
                    frames.add(frame);
                }

                @Override
                public void iterationStarts(int iteration, long seq, Supplier<Checkpoint> checkpoint) {
                    assertNotNull(checkpoint.get());
                }
            };
            assertEquals(diagnosed(c, Probe.NONE), diagnosed(c, everything), name);
            assertTrue(frames.size() > 100, name);
            for (int i = 0; i < frames.size(); i++)
                assertEquals(i + 1, frames.get(i).seq(), name + ": every event has a frame, numbered from 1");
        }
    }

    @Test
    void aRestoredCheckpointContinuesTheSameRun() {
        Case c = cases().get("defaults");
        List<Checkpoint> saved = new ArrayList<>();
        Probe keep = new Probe() {
            @Override
            public boolean frameAt(EventType type, long seq, int iteration) {
                return false;
            }

            @Override
            public void deliver(Frame frame) {
            }

            @Override
            public void iterationStarts(int iteration, long seq, Supplier<Checkpoint> checkpoint) {
                saved.add(checkpoint.get());
            }
        };
        List<String> full = diagnosed(c, keep);
        for (int k : new int[]{0, 3, saved.size() - 1}) {
            Checkpoint cp = saved.get(k);
            for (int twice = 0; twice < 2; twice++) {       // a checkpoint can be restored any number of times
                ColonyContext ctx = new ColonyContext(c.problem(), 12345, c.evaluations());   // the seed comes from the checkpoint
                List<String> resumed = new ArrayList<>();
                ColonyRun run = new ColonyRun(c.settings(), c.problem(), new Candidates(c.problem(), c.settings().candidates()),
                        ctx, new Probe() {
                    @Override
                    public boolean frameAt(EventType type, long seq, int iteration) {
                        return false;
                    }

                    @Override
                    public void deliver(Frame frame) {
                    }

                    @Override
                    public void iterationStarts(int iteration, long seq, Supplier<Checkpoint> checkpoint) {
                        resumed.add(iteration + ": best=" + ctx.bestCost() + " evals=" + ctx.evaluations() + " " + ctx.metrics());
                    }
                });
                run.restore(cp);
                run.iterate();
                resumed.add("end: best=" + ctx.bestCost() + " evals=" + ctx.evaluations() + " " + ctx.metrics());
                assertEquals(full.subList(k, full.size()), resumed, "from iteration " + cp.iteration());
            }
        }
    }
}
