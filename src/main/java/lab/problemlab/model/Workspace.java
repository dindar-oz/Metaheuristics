package lab.problemlab.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * The state of a Problem Lab session and its history. Every change (a new instance, an edit, a generated or mutated
 * solution) adds a {@link LabState}; {@link #undo}, {@link #redo} and {@link #goTo} move through them, and a change
 * made after an undo drops the states that could have been redone. Listeners are told about every move.
 * <p>
 * Changes that come in quick succession, such as nudging a participant with the arrow keys or applying a mutation
 * repeatedly, can share one history entry: a change with the same merge key as the previous one replaces it.
 * Not thread-safe: use it from the Swing thread.
 */
public final class Workspace<S, P> {

    public static final int MAX_HISTORY = 500;

    @FunctionalInterface
    public interface Listener<S, P> {
        // previous is the state shown before the change (null at the start); current is never null
        void stateChanged(LabState<S, P> previous, LabState<S, P> current);
    }

    private final BiFunction<P, S, Evaluation> evaluator;
    private final List<LabState<S, P>> history = new ArrayList<>();
    private final List<Listener<S, P>> listeners = new ArrayList<>();
    private int current = -1;
    private Object mergeKey;        // merge key of the change that made the newest state, null if it cannot be merged

    // evaluator computes the evaluation of a solution of an instance
    public Workspace(BiFunction<P, S, Evaluation> evaluator) {
        this.evaluator = evaluator;
    }

    public void addListener(Listener<S, P> listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener<S, P> listener) {
        listeners.remove(listener);
    }

    // ---- current state

    public boolean hasInstance() {
        return current >= 0;
    }

    // null before the first instance
    public LabState<S, P> state() {
        return current < 0 ? null : history.get(current);
    }

    public P problem() {
        return current < 0 ? null : state().problem();
    }

    public S solution() {
        return current < 0 ? null : state().solution();
    }

    // ---- changes

    // a new instance, without a solution
    public void newInstance(P problem, String action) {
        change(problem, null, action, null);
    }

    // a new solution of the current instance (null removes the solution)
    public void setSolution(S solution, String action) {
        requireInstance();
        change(problem(), solution, action, null);
    }

    // any change: a new instance with a solution of it (or null)
    public void change(P problem, S solution, String action) {
        change(problem, solution, action, null);
    }

    /**
     * As {@link #change(Object, Object, String)}; if mergeKey is not null and equals the merge key of the change that
     * made the current state, which is the newest, that state is replaced instead of adding one.
     */
    public void change(P problem, S solution, String action, Object mergeKey) {
        Objects.requireNonNull(problem, "problem");
        LabState<S, P> previous = state();
        var next = new LabState<>(problem, solution, solution == null ? null : evaluator.apply(problem, solution), action);
        boolean merge = mergeKey != null && mergeKey.equals(this.mergeKey) && current == history.size() - 1;
        if (merge)
            history.set(current, next);
        else {
            history.subList(current + 1, history.size()).clear();
            history.add(next);
            if (history.size() > MAX_HISTORY)
                history.remove(0);
            current = history.size() - 1;
        }
        this.mergeKey = mergeKey;
        fire(previous);
    }

    // ---- history

    public List<LabState<S, P>> history() {
        return Collections.unmodifiableList(history);
    }

    public int currentIndex() {
        return current;
    }

    public boolean canUndo() {
        return current > 0;
    }

    public boolean canRedo() {
        return current < history.size() - 1;
    }

    public void undo() {
        if (canUndo())
            goTo(current - 1);
    }

    public void redo() {
        if (canRedo())
            goTo(current + 1);
    }

    // makes the index-th state of the history the current one
    public void goTo(int index) {
        if (index < 0 || index >= history.size())
            throw new IndexOutOfBoundsException("no state " + index + " in a history of " + history.size());
        if (index == current)
            return;
        LabState<S, P> previous = state();
        current = index;
        mergeKey = null;
        fire(previous);
    }

    private void requireInstance() {
        if (!hasInstance())
            throw new IllegalStateException("there is no instance yet");
    }

    private void fire(LabState<S, P> previous) {
        LabState<S, P> now = state();
        for (Listener<S, P> listener : new ArrayList<>(listeners))
            listener.stateChanged(previous, now);
    }
}
