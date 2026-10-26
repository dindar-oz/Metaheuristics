package alg.ga;

import alg.*;
import problem.OptimizationProblem;
import problem.ProblemModel;
import representation.Representation;
import representation.Solution;
import utils.RandUtils;

import java.util.ArrayList;
import java.util.List;

public class GA<PM extends ProblemModel<R>, R extends Representation> extends BaseMetaheuristic<PM,R> {

    InitialSolutionGenerator<PM,R> isg;
    int populationSize;
    List<Solution> population;
    ParentSelector parentSelector;
    VictimSelector victimSelector;
    private double crossOverRate;
    private CrossOverOperator crossOverOperator;
    private double mutationRate;
    private MutationOperation mutationOperator;

    public GA(TerminalCondition<PM, R> terminalCondition) {
        super(terminalCondition);
    }

    @Override
    protected Solution _perform(OptimizationProblem<PM, R> problem) {
        while (!terminalCondition.isSatisfied(this,problem))
        {
            List<Solution> parents = selectParents(problem);
            List<Solution> offspring = produceOffspring(problem,parents);
            applyMutation(problem,offspring);
            replacePopulation(problem,offspring);
            iterationCount++;
        }
        return bestSolution;
    }

    private void replacePopulation(OptimizationProblem<PM, R> problem, List<Solution> offspring) {
        List<Solution> victims = victimSelector.select(problem, population, offspring.size());
        population.removeAll(victims);
        population.addAll(offspring);
    }

    private void applyMutation(OptimizationProblem<PM, R> problem, List<Solution> offspring) {
        for (Solution s : offspring)
        {
            if (RandUtils.rollDice(mutationRate))
            {
                mutationOperator.apply(problem,s);
                updateBest(problem,s);
            }
        }
    }

    private List<Solution> selectParents(OptimizationProblem<PM, R> problem) {

        List<Solution> parents = parentSelector.select(problem, population);
        return parents;
    }

    private List<Solution> produceOffspring(OptimizationProblem<PM, R> problem, List<Solution> parents) {

        List<Solution> offSpring = new ArrayList<>();

        for (int p1 = 0; p1 < parents.size(); p1++) {
            for (int p2 = p1+1; p2 < parents.size(); p2++) {
                if (RandUtils.rollDice(crossOverRate))
                {
                    Solution parent1 = parents.get(p1);
                    Solution parent2 = parents.get(p2);

                    List<Solution> children =  crossOverOperator.apply(problem,parent1,parent2);
                    offSpring.addAll(children);
                    updateBest(problem,children);
                }

            }
        }

        return offSpring;
    }

    @Override
    protected void _init(OptimizationProblem<PM, R> problem) {
        createInitialPopulation(problem);
        iterationCount = 0;
        setBestAchieveTime(0);
    }

    private void createInitialPopulation(OptimizationProblem<PM, R> problem) {
        population=  new ArrayList<>();
        for (int i = 0; i < populationSize ; i++) {
            Solution s = isg.generate(problem);
            updateBest(problem,s);
            population.add(s);
        }
    }

    @Override
    public String toString() {
        return "GA " + crossOverOperator + "CR:" + crossOverRate +
                mutationOperator + " MR:" + mutationRate +
                " POP:" + populationSize;
    }

    public void setIsg(InitialSolutionGenerator<PM, R> isg) {
        this.isg = isg;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public void setParentSelector(ParentSelector parentSelector) {
        this.parentSelector = parentSelector;
    }

    public void setVictimSelector(VictimSelector victimSelector) {
        this.victimSelector = victimSelector;
    }

    public void setCrossOverRate(double crossOverRate) {
        this.crossOverRate = crossOverRate;
    }

    public void setCrossOverOperator(CrossOverOperator crossOverOperator) {
        this.crossOverOperator = crossOverOperator;
    }

    public void setMutationRate(double mutationRate) {
        this.mutationRate = mutationRate;
    }

    public void setMutationOperator(MutationOperation mutationOperator) {
        this.mutationOperator = mutationOperator;
    }
}
