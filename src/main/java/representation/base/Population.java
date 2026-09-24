package representation.base;

import representation.CostBasedComparator;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Created by dindar.oz on 25.06.2015.
 */
public interface Population {

    public List<Individual> getIndividuals();
    public int size();
    public Individual get(int i);
    public Population clone();
    public void add(Individual i);
    public void add(List<Individual> i);
    public void add(Individual[] i);
    public void add(Population i);
    public void remove(int i);
    public void remove(Individual i);
    public Individual getBest();
    public double getBestCost();
    public boolean isEmpty();
    public boolean contains(Individual i);

    void removeAll(Collection<Individual> victims);

    void sort(CostBasedComparator comparator);

    Population subPopulation(int i, int i1);

    void clear();

    void removeIf(Predicate<Individual> filter);

    void forEach(Consumer<Individual> consumer);
}
