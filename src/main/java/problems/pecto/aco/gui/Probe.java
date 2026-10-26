package problems.pecto.aco.gui;

import java.util.function.Supplier;

/**
 * How a diagnosed run reports to whoever watches it. At every event the engine first asks {@link #frameAt}; only if
 * the answer is yes does it record the details of the decision and build a {@link Frame}, which it hands to
 * {@link #deliver}. Nothing a probe does may change the run: recording only reads, and every draw stays the same.
 */
interface Probe {

    Probe NONE = new Probe() {
        @Override
        public boolean frameAt(EventType type, long seq, int iteration) {
            return false;
        }

        @Override
        public void deliver(Frame frame) {
        }
    };

    // called at every event, so it must be cheap; may throw to end the run (e.g. to rewind it)
    boolean frameAt(EventType type, long seq, int iteration);

    // may block (to pause the run) or throw to end it
    void deliver(Frame frame);

    // at the start of every iteration: the probe may take a checkpoint to resume from later
    default void iterationStarts(int iteration, long seq, Supplier<Checkpoint> checkpoint) {
    }
}
