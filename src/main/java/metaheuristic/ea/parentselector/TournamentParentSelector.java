package metaheuristic.ea.parentselector;

import base.OptimizationProblem;
import metaheuristic.ea.base.ParentSelector;
import representation.ListPopulation;
import representation.base.Individual;
import representation.base.Population;
import util.random.RandUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by dindar.oz on 28.05.2015.
 */
public class TournamentParentSelector implements ParentSelector {

    public static int DEFAULT_TOURNAMENT_SIZE = 5;
    private int tournamentSize;

    int parentCount =2;

    public TournamentParentSelector(int tournamentSize,int parentCount) {
        this.tournamentSize = tournamentSize;
        this.parentCount = parentCount;
    }

    public TournamentParentSelector(int tournamentSize)
    {
        this.tournamentSize = tournamentSize;
        this.parentCount = -1;
    }

    public TournamentParentSelector() {

        this.parentCount = -1;
        this.tournamentSize = DEFAULT_TOURNAMENT_SIZE;
    }


    List<Individual> copyPopulation( List<Individual> population)
    {
        List<Individual> copied = new ArrayList<>();
        for (Individual i:population)
            copied.add(i);

        return copied;
    }

    @Override
    public List<Individual> selectParents(OptimizationProblem problem, List<Individual> population) {

        List<Individual> copied = copyPopulation(population);

        List<Individual> parents = new ArrayList<>();

        if (parentCount == -1 )
            parentCount = population.size()/2;

        for (int c= 0; c< parentCount;c++)
        {
            Individual parent = selectParent(problem,copied);
            copied.remove(parent);
            parents.add(parent);
        }

        return parents;




    }

    private Individual selectParent(OptimizationProblem problem, List<Individual> population) {
        Population pool = selectPool(population);
        return pool.getBest();
    }

    private Population selectPool(List<Individual> population) {
        Population pool = new ListPopulation();

        List<Integer> selected = RandUtil.randIntSet(population.size(), tournamentSize);

        for (int c =0; c<tournamentSize;c++)
        {
            pool.add(population.get(selected.get(c)));
        }
        return pool;
    }
}
