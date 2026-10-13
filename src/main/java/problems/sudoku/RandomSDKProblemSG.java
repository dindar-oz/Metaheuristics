package problems.sudoku;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import util.random.RNG;

/**
 * Starts the search from the puzzle's initial board.
 */
public class RandomSDKProblemSG implements InitialSolutionGenerator<SDKRepresentation> {

    private final SDKProblem problem;

    public RandomSDKProblemSG(SDKProblem problem) {
        this.problem = problem;
    }

    @Override
    public SDKRepresentation generate(OptimizationProblem<SDKRepresentation> ignored, RNG rng) {
        return new SDKRepresentation(problem.blockSize, problem.initialBoard).copy();
    }
}
