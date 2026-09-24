package examples;

import base.MutationOperator;
import base.TerminalCondition;
import base.terminalcondition.CostBasedTC;
import base.terminalcondition.NeighboringBasedTC;
import experiments.ConsoleERH;
import experiments.Experiment;
import experiments.ExperimentCase;
import metaheuristic.ea.EA;
import metaheuristic.ea.parentselector.CostBasedParentSelector;
import metaheuristic.sa.GeometricCooling;
import metaheuristic.sa.SA;
import problems.sudoku.PlacementMutation;
import problems.sudoku.RandomSDKProblemSG;
import problems.sudoku.RemovalMutation;
import problems.sudoku.SDKProblem;

import java.util.Arrays;
import java.util.Collections;
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
        RandomSDKProblemSG isg = new RandomSDKProblemSG();
        List<MutationOperator> operators = Arrays.asList(new PlacementMutation(), new RemovalMutation());

        EA ea = new EA(Collections.emptyList(), operators, new CostBasedParentSelector(2), budget());
        ea.setImmigrationPeriod(10000);

        SA sa = new SA(operators, new GeometricCooling(0.999), 100, 1e-5, budget());

        new Experiment(Arrays.asList(
                new ExperimentCase("EA", ea, problem, isg),
                new ExperimentCase("SA", sa, problem, isg)),
                3, Collections.singletonList(new ConsoleERH())).run();
    }

    static TerminalCondition budget() {
        return TerminalCondition.anyOf(new NeighboringBasedTC(200000), new CostBasedTC(0));
    }
}
