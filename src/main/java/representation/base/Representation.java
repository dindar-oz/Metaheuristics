package representation.base;

/**
 * A candidate solution of type S (the implementing class itself, e.g. IntegerPermutation implements
 * Representation&lt;IntegerPermutation&gt;). Implementations must provide a deep copy() and value-based
 * equals()/hashCode(). Once handed to an algorithm, a representation must not be modified: operators
 * always work on a copy.
 */
public interface Representation<S extends Representation<S>> {

    S copy();

    default double distanceTo(S other) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not define a distance");
    }
}
