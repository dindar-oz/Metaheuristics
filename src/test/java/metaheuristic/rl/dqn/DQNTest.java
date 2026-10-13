package metaheuristic.rl.dqn;

import metaheuristic.Metaheuristic;
import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.run.StopReason;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.WFLOPCase;
import problems.wflop.WindScenario;
import problems.wflop.rl.ConstructiveWFLOP;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import metaheuristic.rl.GreedyConstruction;
import metaheuristic.rl.nn.Mlp;

import static org.junit.jupiter.api.Assertions.*;

class DQNTest {

    private static final DiscreteWFLOP DISCRETE = WFLOPCase.CASE_3.createDiscrete(WindScenario.condition1(), 10);
    private static final ConstructiveWFLOP PROBLEM = new ConstructiveWFLOP(DISCRETE);

    private static DQN<CellLayout> dqn() {
        return DQN.<CellLayout>builder().warmup(100).epsilon(1, 0.05, 50).targetUpdate(100).build();
    }

    private static RunResult<CellLayout> run(long seed, int evaluations) {
        return Runner.of(dqn()).seed(seed).stopWhen(new EvaluationBasedTC(evaluations)).run(PROBLEM);
    }

    @Test
    void sameSeedGivesIdenticalRun() {
        RunResult<CellLayout> a = run(7, 150);
        RunResult<CellLayout> b = run(7, 150);
        assertEquals(a.bestCost(), b.bestCost());
        assertEquals(a.bestSolution(), b.bestSolution());
        assertEquals(a.iterations(), b.iterations());
    }

    @Test
    void oneEvaluationPerEpisodeAndExactBudget() {
        RunResult<CellLayout> r = run(3, 120);
        assertEquals(StopReason.CONDITION_MET, r.stopReason());
        assertEquals(120, r.evaluations());
        assertTrue(r.iterations() >= 119, "an iteration per episode");
        assertTrue(DISCRETE.isFeasible(r.bestSolution()));
        assertEquals(r.bestCost(), DISCRETE.cost(r.bestSolution()), 1e-12);
    }

    // The learned policy must beat purely random construction (epsilon = 1) on the same budget and seeds
    @Test
    void learningBeatsRandomConstruction() {
        DQN<CellLayout> random = DQN.<CellLayout>builder().epsilon(1, 1, 1).greedyEvery(0).build();
        double learned = 0, randomCost = 0;
        for (long seed = 1; seed <= 3; seed++) {
            learned += run(seed, 300).bestCost();
            randomCost += Runner.of(random).seed(seed).stopWhen(new EvaluationBasedTC(300)).run(PROBLEM).bestCost();
        }
        assertTrue(learned < randomCost, "DQN " + learned / 3 + " vs random " + randomCost / 3);
    }

    @Test
    void softmaxExplorationIsReproducibleAndLearns() {
        DQN<CellLayout> softmax = DQN.<CellLayout>builder().warmup(100).softmax(1, 0.05, 300).targetUpdate(100).build();
        DQN<CellLayout> random = DQN.<CellLayout>builder().epsilon(1, 1, 1).greedyEvery(0).warmup(Integer.MAX_VALUE).build();
        RunResult<CellLayout> a = Runner.of(softmax).seed(1).stopWhen(new EvaluationBasedTC(300)).run(PROBLEM);
        RunResult<CellLayout> b = Runner.of(softmax).seed(1).stopWhen(new EvaluationBasedTC(300)).run(PROBLEM);
        assertEquals(a.bestCost(), b.bestCost());
        assertEquals(a.bestSolution(), b.bestSolution());
        assertTrue(a.finalProgress().metrics().containsKey(DQN.TEMPERATURE));

        double learned = a.bestCost(), randomCost = 0;
        for (long seed = 1; seed <= 3; seed++) {
            if (seed > 1)
                learned += Runner.of(softmax).seed(seed).stopWhen(new EvaluationBasedTC(300)).run(PROBLEM).bestCost();
            randomCost += Runner.of(random).seed(seed).stopWhen(new EvaluationBasedTC(300)).run(PROBLEM).bestCost();
        }
        assertTrue(learned < randomCost, "softmax DQN " + learned / 3 + " vs random " + randomCost / 3);
    }

    // A run hands over its network also when its budget stops it; a frozen run uses a given network without changing it
    @Test
    void trainedNetworksCanBeReusedFrozen() {
        AtomicReference<Mlp> trained = new AtomicReference<>();
        DQN<CellLayout> learner = DQN.<CellLayout>builder().warmup(100).trainedNetwork(trained::set).build();
        Runner.of(learner).seed(1).stopWhen(new EvaluationBasedTC(150)).run(PROBLEM);
        assertNotNull(trained.get());

        AtomicReference<Mlp> used = new AtomicReference<>();
        DQN<CellLayout> frozen = DQN.<CellLayout>builder().initialNetwork(trained.get()).training(false)
                .softmax(0.2, 0.2, 1).trainedNetwork(used::set).build();
        RunResult<CellLayout> a = Runner.of(frozen).seed(2).stopWhen(new EvaluationBasedTC(60)).run(PROBLEM);
        assertTrue(used.get().hasSameParameters(trained.get()), "a frozen network must not change");
        RunResult<CellLayout> b = Runner.of(frozen).seed(2).stopWhen(new EvaluationBasedTC(60)).run(PROBLEM);
        assertEquals(a.bestCost(), b.bestCost());
        assertEquals(a.bestSolution(), b.bestSolution());
    }

    // With the reward baseline, a new network adds nothing to the known rewards: its greedy episode is the greedy heuristic
    @Test
    void untrainedNetworkIsTheGreedyHeuristic() {
        RunResult<CellLayout> greedy = Runner.of(new GreedyConstruction<CellLayout>()).seed(1)
                .stopWhen(new EvaluationBasedTC(100)).run(PROBLEM);
        assertEquals(StopReason.ALGORITHM_FINISHED, greedy.stopReason());
        assertEquals(1, greedy.evaluations());
        assertTrue(DISCRETE.isFeasible(greedy.bestSolution()));

        DQN<CellLayout> untrained = DQN.<CellLayout>builder().training(false).epsilon(0, 0, 1).build();
        RunResult<CellLayout> first = Runner.of(untrained).seed(1).stopWhen(new EvaluationBasedTC(1)).run(PROBLEM);
        assertEquals(greedy.bestSolution(), first.bestSolution());

        // without the baseline, a random network is not the greedy heuristic
        DQN<CellLayout> plain = DQN.<CellLayout>builder().rewardBaseline(false).training(false).epsilon(0, 0, 1).build();
        assertNotEquals(greedy.bestSolution(),
                Runner.of(plain).seed(1).stopWhen(new EvaluationBasedTC(1)).run(PROBLEM).bestSolution());
    }

    @Test
    void networksForOtherFeaturesAreRejected() {
        Mlp wrong = new Mlp(PROBLEM.featureCount() + 1, new int[]{4}, new util.random.MersenneTwisterRNG(1));
        DQN<CellLayout> dqn = DQN.<CellLayout>builder().initialNetwork(wrong).build();
        assertThrows(IllegalArgumentException.class,
                () -> Runner.of(dqn).seed(1).stopWhen(new EvaluationBasedTC(10)).run(PROBLEM));
    }

    // viewing() runs DQN on the plain DiscreteWFLOP, as the other algorithms are run
    @Test
    void viewingRunsOnTheDiscreteProblem() {
        Metaheuristic<CellLayout, DiscreteWFLOP> onDiscrete = dqn().viewing(ConstructiveWFLOP::new);
        RunResult<CellLayout> viewed = Runner.of(onDiscrete).seed(5).stopWhen(new EvaluationBasedTC(100)).run(DISCRETE);
        assertEquals(run(5, 100).bestCost(), viewed.bestCost());
        assertEquals("DQN", onDiscrete.getName());
    }

    @Test
    void concurrentRunsOfOneInstanceAreIndependent() throws Exception {
        DQN<CellLayout> shared = dqn();
        double expected = Runner.of(shared).seed(9).stopWhen(new EvaluationBasedTC(80)).run(PROBLEM).bestCost();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Double>> futures = new ArrayList<>();
            for (int i = 0; i < 6; i++)
                futures.add(pool.submit(() ->
                        Runner.of(shared).seed(9).stopWhen(new EvaluationBasedTC(80)).run(PROBLEM).bestCost()));
            for (Future<Double> f : futures)
                assertEquals(expected, f.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
