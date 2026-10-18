package problems.pecto.aco;

import metaheuristic.run.RunResult;
import metaheuristic.run.Runner;
import metaheuristic.termination.EvaluationBasedTC;
import org.junit.jupiter.api.Test;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.operators.GreedyPECTOSG;

import static org.junit.jupiter.api.Assertions.*;

class PECTOACOTest {

    private static RunResult<PECTOSolution> run(PECTOACO aco, PECTOProblem problem, long seed, int evaluations) {
        return Runner.of(aco).seed(seed).stopWhen(new EvaluationBasedTC(evaluations)).run(problem);
    }

    @Test
    void improvesOnTheGreedySolution() {
        PECTOProblem problem = PECTOInstances.random(6, 80, 4, 25, 8);
        RunResult<PECTOSolution> result = run(PECTOACO.builder().build(), problem, 3, 3000);
        assertTrue(problem.isFeasible(result.bestSolution()));
        assertEquals(problem.cost(result.bestSolution()), result.bestCost());
        assertTrue(result.bestCost() < problem.cost(GreedyPECTOSG.complete(problem.emptySolution())));
        assertTrue(result.evaluations() <= 3000);
    }

    @Test
    void antsBuildFeasibleSolutionsWhenEveryBedIsNeeded() {
        PECTOProblem problem = PECTOInstances.random(8, 90, 3, 30, 2);
        PECTOACO aco = PECTOACO.builder().antCount(5).localSearchSteps(0).optimalHospitals(false).shortlist(8).build();
        RunResult<PECTOSolution> result = run(aco, problem, 1, 200);
        assertTrue(problem.isFeasible(result.bestSolution()));
    }

    @Test
    void sameSeedSameRun() {
        PECTOProblem problem = PECTOInstances.random(5, 40, 3, 20, 5);
        PECTOACO aco = PECTOACO.builder().antCount(10).build();
        assertEquals(run(aco, problem, 7, 800).bestCost(), run(aco, problem, 7, 800).bestCost());
    }

    @Test
    void synchronousRunIsTheSequentialRun() {
        PECTOProblem problem = PECTOInstances.random(8, 120, 5, 30, 4);
        RunResult<PECTOSolution> sequential = run(PECTOACO.builder().antCount(7).build(), problem, 11, 2000);
        for (int threads : new int[]{1, 3, 16}) {
            PECTOACO aco = PECTOACO.builder().antCount(7)
                    .executionMode(PECTOACO.ExecutionMode.SYNCHRONOUS).threads(threads).build();
            RunResult<PECTOSolution> synchronous = run(aco, problem, 11, 2000);
            assertEquals(sequential.bestCost(), synchronous.bestCost(), "threads=" + threads);
            assertEquals(sequential.evaluations(), synchronous.evaluations(), "threads=" + threads);
            assertArrayEquals(sequential.bestSolution().toSequence(), synchronous.bestSolution().toSequence(),
                    "threads=" + threads);
        }
    }

    @Test
    void explorersAreOnByDefault() {
        PECTOProblem problem = PECTOInstances.random(6, 80, 4, 25, 8);
        PECTOACO explicit = PECTOACO.builder().antCount(6).explorers(2).explorerRate(0.05).explorerBeta(0.5)
                .explorerDeposit(0.05).tauMinRatio(0).build();
        RunResult<PECTOSolution> byDefault = run(PECTOACO.builder().antCount(6).build(), problem, 4, 1500);
        assertEquals(byDefault.bestCost(), run(explicit, problem, 4, 1500).bestCost());
        assertTrue(byDefault.finalProgress().metrics().containsKey(PECTOACO.EXPLORER_WINS));
        RunResult<PECTOSolution> plain = run(PECTOACO.builder().antCount(6).explorers(0).build(), problem, 4, 1500);
        assertFalse(plain.finalProgress().metrics().containsKey(PECTOACO.EXPLORER_WINS));
    }

    @Test
    void explorersThatNeitherExploreNorDepositAreOrdinaryAnts() {
        PECTOProblem problem = PECTOInstances.random(8, 120, 5, 30, 4);
        RunResult<PECTOSolution> plain = run(PECTOACO.builder().antCount(7).explorers(0).build(), problem, 2, 2000);
        RunResult<PECTOSolution> idle = run(PECTOACO.builder().antCount(7).explorers(3).explorerRate(0).explorerDeposit(0)
                .build(), problem, 2, 2000);
        assertEquals(plain.bestCost(), idle.bestCost());
        assertArrayEquals(plain.bestSolution().toSequence(), idle.bestSolution().toSequence());
    }

    @Test
    void explorersBuildFeasibleSolutions() {
        PECTOProblem problem = PECTOInstances.random(8, 90, 3, 30, 2);
        for (double rate : new double[]{0.05, 0.5, 1})
            for (double explorerBeta : new double[]{0, 0.5, 3}) {
                PECTOACO aco = PECTOACO.builder().antCount(5).explorers(5).explorerRate(rate).explorerBeta(explorerBeta)
                        .localSearchSteps(0).optimalHospitals(false).build();
                assertTrue(problem.isFeasible(run(aco, problem, 1, 200).bestSolution()),
                        "rate=" + rate + ", explorerBeta=" + explorerBeta);
            }
    }

    @Test
    void explorersKeepTheSynchronousRunTheSequentialRun() {
        PECTOProblem problem = PECTOInstances.random(8, 120, 5, 30, 4);
        PECTOACO.Builder b = PECTOACO.builder().antCount(7).explorers(4).explorerRate(0.3).explorerDeposit(0.2)
                .tauMinRatio(0.025);
        RunResult<PECTOSolution> sequential = run(b.build(), problem, 11, 2000);
        RunResult<PECTOSolution> synchronous = run(b.executionMode(PECTOACO.ExecutionMode.SYNCHRONOUS).threads(3).build(),
                problem, 11, 2000);
        assertEquals(sequential.bestCost(), synchronous.bestCost());
        assertArrayEquals(sequential.bestSolution().toSequence(), synchronous.bestSolution().toSequence());
    }

    @Test
    void handlesTinyInstances() {
        PECTOACO aco = PECTOACO.builder().candidates(50).shortlist(10).build();
        for (PECTOProblem problem : new PECTOProblem[]{
                PECTOInstances.random(1, 1, 1, 1, 1), PECTOInstances.random(3, 2, 1, 2, 1), PECTOInstances.random(2, 0, 1, 0, 1)}) {
            RunResult<PECTOSolution> result = run(aco, problem, 1, 100);
            assertTrue(problem.isFeasible(result.bestSolution()));
        }
    }

    @Test
    void rejectsInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().rho(0).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().pBest(1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().shortlist(0).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().hospitalCandidates(0).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().localSearchSteps(-1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().threads(0).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().executionMode(null).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().tauMinRatio(1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().tauMinRatio(-0.1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().antCount(5).explorers(6).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().explorers(-1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().explorerRate(-0.1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().explorerRate(1.1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().explorerBeta(-1).build());
        assertThrows(IllegalArgumentException.class, () -> PECTOACO.builder().explorerDeposit(-1).build());
    }
}
