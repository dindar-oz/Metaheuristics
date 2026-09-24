package representation.base;

/**
 * A candidate solution. Implementations must provide a deep clone() and value-based equals()/hashCode().
 */
public interface Representation {

    Representation clone();

    default double distanceTo(Representation r) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " does not define a distance");
    }
}
