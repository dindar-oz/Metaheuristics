package metaheuristic;

import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import representation.base.Representation;

/**
 * Created by dindar.oz on 17.06.2015.
 */
public interface MetaHeuristic {

    void perform(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator);

    String getName();

    int getIterationCount();

    // number of objective function evaluations so far
    long getNeighboringCount();

    double getBestKnownCost();

    Representation getBestKnownSolution();

    // milliseconds from the start of the run until the best known solution was found
    long getBestAchieveTime();
}
