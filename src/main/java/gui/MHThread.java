package gui;

import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.MetaHeuristicListener;
import base.InitialSolutionGenerator;

/**
 * Created by dindar.oz on 25.06.2015.
 */
public class MHThread implements Runnable{

    AbstractMetaheuristic metaHeuristic;
    InitialSolutionGenerator solutionGenerator;
    OptimizationProblem problem;


    public MHThread(AbstractMetaheuristic metaHeuristic, InitialSolutionGenerator solutionGenerator,OptimizationProblem problem, MetaHeuristicListener listener) {
        this.metaHeuristic = metaHeuristic;
        this.solutionGenerator = solutionGenerator;
        this.problem = problem;
        this.metaHeuristic.addListener(listener);
    }

    public void addListener(MetaHeuristicListener listener)
    {
        metaHeuristic.addListener(listener);
    }

    @Override
    public void run() {
        metaHeuristic.perform(problem,solutionGenerator);
    }
}
