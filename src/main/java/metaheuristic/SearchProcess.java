package metaheuristic;

/**
 * One run of a {@link SteppableMetaheuristic}, advanced one iteration at a time by its owner (the algorithm's own
 * search(), or a scheduler such as metaheuristic.island.IslandModel). It holds the run's state; all of it lives on the
 * run's thread, like the SearchContext it was started with.
 * <p>
 * A process may implement {@link Emigration} and {@link Immigration}, through which a scheduler exchanges solutions
 * between runs. Between two steps it is in a consistent state, so exchanges happen only there.
 */
public interface SearchProcess extends AutoCloseable {

    /**
     * One iteration (generation, ant colony iteration, ...). Leaves early, without completing the iteration, when the
     * context's stop condition holds; it is then {@link #finished}.
     */
    void step();

    // true when the process can make no more steps: it stopped, or the algorithm has nothing left to do
    boolean finished();

    // frees what the run holds (e.g. threads); after close() the process makes no more steps
    @Override
    default void close() {
    }
}
