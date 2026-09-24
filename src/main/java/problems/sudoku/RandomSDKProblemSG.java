package problems.sudoku;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import representation.base.Representation;
import util.random.RNG;

/**
 * Starts the search from the puzzle's initial board.
 */
public class RandomSDKProblemSG implements InitialSolutionGenerator {

    @Override
    public Representation generate(OptimizationProblem problem, RNG rng) {
        SDKProblem sdkProblem = (SDKProblem) problem;
        return new SDKRepresentation(sdkProblem.blockSize, sdkProblem.initialBoard).clone();
    }
}
