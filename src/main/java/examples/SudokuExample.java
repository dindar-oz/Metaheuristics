package examples;

import base.MutationOperator;
import experiments.ConsoleERH;
import experiments.Experiment;
import experiments.ExperimentCase;
import metaheuristic.ea.EA;
import metaheuristic.ea.hook.ImmigrationHook;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import metaheuristic.termination.CostBasedTC;
import metaheuristic.termination.EvaluationBasedTC;
import metaheuristic.termination.TerminalCondition;
import problems.sudoku.PlacementMutation;
import problems.sudoku.RandomSDKProblemSG;
import problems.sudoku.RemovalMutation;
import problems.sudoku.SDKProblem;
import problems.sudoku.SDKRepresentation;

import java.util.Arrays;
import java.util.List;

/**
 * Fills a 9x9 sudoku with a mutation-only EA and with SA. A cost of 0 means the board is solved.
 */
public class SudokuExample {

    public static void main(String[] args) {
        int[][] initialBoard = new int[9][9];
        initialBoard[0][3] = 3;
        initialBoard[4][2] = 6;
        initialBoard[0][2] = 1;
        initialBoard[8][8] = 8;
        initialBoard[5][5] = 5;
        initialBoard[5][6] = 4;

        SDKProblem problem = new SDKProblem(3, initialBoard);
        RandomSDKProblemSG isg = new RandomSDKProblemSG(problem);
        List<MutationOperator<SDKRepresentation>> operators =
                Arrays.asList(new PlacementMutation(problem), new RemovalMutation(problem));

        EA<SDKRepresentation> ea = EA.builder(isg)
                .mutations(operators)
                .hook(new ImmigrationHook<>(10000, 20))
                .build();

        SA<SDKRepresentation> sa = SA.builder(operators, isg)
                .cooling(new GeometricCooling(0.999))
                .initialTemperature(100)
                .minimumTemperature(1e-5)
                .build();

        Experiment.of(
                        ExperimentCase.of("EA", ea, problem),
                        ExperimentCase.of("SA", sa, problem))
                .repeats(3)
                .seed(42)
                .stopWhen(TerminalCondition.anyOf(new EvaluationBasedTC(200000), new CostBasedTC(0)))
                .threads(Runtime.getRuntime().availableProcessors())
                .handler(new ConsoleERH())
                .run();
    }
}
