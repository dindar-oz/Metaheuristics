package representation.base;

import java.util.Comparator;

/**
 * An evaluated solution. Immutable: its representation must not be modified after the individual is created
 * (operators always return new representations), so individuals can be shared freely.
 */
public final class Individual<S extends Representation<S>> {

    public static final Comparator<Individual<?>> BY_COST = Comparator.comparingDouble(Individual::getCost);

    private final S representation;
    private final double cost;
    private final long birthGeneration;

    public Individual(S representation, double cost, long birthGeneration) {
        this.representation = representation;
        this.cost = cost;
        this.birthGeneration = birthGeneration;
    }

    public Individual(S representation, double cost) {
        this(representation, cost, 0);
    }

    public S getRepresentation() {
        return representation;
    }

    public double getCost() {
        return cost;
    }

    public long getBirthGeneration() {
        return birthGeneration;
    }

    public long getAge(long currentGeneration) {
        return currentGeneration - birthGeneration;
    }

    @Override
    public String toString() {
        return "Cost: " + cost + "  " + representation;
    }
}
