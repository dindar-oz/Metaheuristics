package representation;

import representation.base.Individual;
import representation.base.Population;
import util.PopulationUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Created by dindar.oz on 25.06.2015.
 */
public class ListPopulation implements Population {

    List<Individual> population;
    Individual best= null;

    public ListPopulation() {
        population = new ArrayList<>();
    }

    public ListPopulation(List<Individual> population, Individual best) {
        this.population = population;
        this.best=best;
    }

    public ListPopulation(List<Individual> sublist) {
        population = sublist;
        best = PopulationUtil.bestIndividual(population);
    }

    /*public ListPopulation(List<Individual> sublist) {
        population = new ArrayList<>();
        for (Individual i:sublist)
            add(i);
    }*/
    @Override
    public List<Individual> getIndividuals() {
        return population;
    }

    @Override
    public int size() {
        return population.size();
    }

    @Override
    public Individual get(int i) {
        return population.get(i);
    }

    @Override
    public Population clone() {
        List<Individual> copyList = new ArrayList<>();
        for (Individual i:population)
            copyList.add(i.clone());

        return new ListPopulation(copyList,best.clone());
    }

    @Override
    public void add(Individual i) {
        updateBestIfNecessary(i);
        population.add(i);
    }

    @Override
    public void add(List<Individual> list) {
        for (Individual i :list)
            add(i);
    }

    @Override
    public void add(Individual[] list) {
        for (Individual i :list)
            add(i);
    }

    private void updateBestIfNecessary(Individual i)
    {
        if (i==null)
            return;
        if (best ==null || best.getCost()>i.getCost())
        {
            best = i.clone();
        }
    }

    @Override
    public void add(Population p) {
        if (p.isEmpty())
            return;
        updateBestIfNecessary(p.getBest());
        population.addAll(p.getIndividuals());
    }

    @Override
    public void remove(int i) {
        Individual removed = population.get(i);
        remove(removed);
    }


    @Override
    public void remove(Individual i) {
        population.remove(i);
        if (i.getRepresentation().equals(best.getRepresentation()))
            best = PopulationUtil.bestIndividual(population);
    }

    @Override
    public Individual getBest() {
        return best;
    }

    @Override
    public double getBestCost() {
        if (best==null)
            return Double.MAX_VALUE;
        return best.getCost();
    }

    @Override
    public boolean isEmpty() {
        return population.isEmpty();
    }

    @Override
    public boolean contains(Individual i) {
        for (Individual pi:population)
        {
            if (pi.getRepresentation().equals(i.getRepresentation()))
                return true;
        }
        return false;
    }

    @Override
    public void removeAll(Collection<Individual> individuals) {

        boolean bestChanged=false;
        for (Individual i :individuals)
            if (i.getRepresentation().equals(best.getRepresentation()))
                bestChanged = true;

        population.removeAll(individuals);
        if (bestChanged)
            best = PopulationUtil.bestIndividual(population);
    }

    @Override
    public void sort(CostBasedComparator comparator) {
    	population.sort(comparator);
    }

    @Override
    public Population subPopulation(int from, int to)
    {
        List<Individual> sublist = population.subList(from,to);
        return new ListPopulation(sublist);
    }
    
    @Override
    public void clear() {
        population.clear();
        best = null;
    }

    @Override
    public void removeIf(Predicate<Individual> filter) {
        boolean bestRemoved =  (filter.test(best));

        population.removeIf(filter);

        if (bestRemoved)
            best = PopulationUtil.bestIndividual(population);
    }

    @Override
    public void forEach(Consumer<Individual> consumer) {
        population.forEach(consumer);
    }


}
