package representation.base;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A mutable collection of individuals, owned by a single run (not thread-safe).
 * Removal is by identity; duplicates are detected by representation equality.
 */
public final class Population<S extends Representation<S>> {

    private final List<Individual<S>> individuals;

    public Population() {
        individuals = new ArrayList<>();
    }

    public Population(Collection<Individual<S>> individuals) {
        this.individuals = new ArrayList<>(individuals);
    }

    // read-only view
    public List<Individual<S>> individuals() {
        return Collections.unmodifiableList(individuals);
    }

    public int size() {
        return individuals.size();
    }

    public boolean isEmpty() {
        return individuals.isEmpty();
    }

    public Individual<S> get(int i) {
        return individuals.get(i);
    }

    public void add(Individual<S> individual) {
        individuals.add(individual);
    }

    public void addAll(Collection<Individual<S>> newcomers) {
        individuals.addAll(newcomers);
    }

    public boolean remove(Individual<S> individual) {
        for (int i = 0; i < individuals.size(); i++)
            if (individuals.get(i) == individual) {
                individuals.remove(i);
                return true;
            }
        return false;
    }

    public void removeAll(Collection<Individual<S>> victims) {
        Set<Individual<S>> set = Collections.newSetFromMap(new IdentityHashMap<>());
        set.addAll(victims);
        individuals.removeIf(set::contains);
    }

    public void removeIf(Predicate<Individual<S>> filter) {
        individuals.removeIf(filter);
    }

    // true if an individual with an equal representation is present
    public boolean containsRepresentation(S r) {
        for (Individual<S> i : individuals)
            if (i.getRepresentation().equals(r))
                return true;
        return false;
    }

    // null if empty
    public Individual<S> best() {
        Individual<S> best = null;
        for (Individual<S> i : individuals)
            if (best == null || i.getCost() < best.getCost())
                best = i;
        return best;
    }

    public double meanCost() {
        double sum = 0;
        for (Individual<S> i : individuals)
            sum += i.getCost();
        return individuals.isEmpty() ? Double.NaN : sum / individuals.size();
    }

    // the count best (lowest cost) individuals, best first
    public List<Individual<S>> best(int count) {
        List<Individual<S>> sorted = sorted();
        return new ArrayList<>(sorted.subList(0, Math.min(count, sorted.size())));
    }

    // the count worst individuals, worst first
    public List<Individual<S>> worst(int count) {
        List<Individual<S>> sorted = sorted();
        Collections.reverse(sorted);
        return new ArrayList<>(sorted.subList(0, Math.min(count, sorted.size())));
    }

    // a sorted copy, best first
    public List<Individual<S>> sorted() {
        List<Individual<S>> copy = new ArrayList<>(individuals);
        copy.sort(Individual.BY_COST);
        return copy;
    }
}
