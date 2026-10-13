package metaheuristic.run;

/**
 * Thrown by SearchContext.evaluate() when the run must stop. Algorithms must not catch it; the Runner does.
 */
public final class SearchStoppedException extends RuntimeException {
    SearchStoppedException() {
        super(null, null, false, false);
    }
}
