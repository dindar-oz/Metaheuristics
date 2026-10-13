package lab.problemlab.model;

/**
 * One step of a Problem Lab session: the instance, the solution (null if there is none yet), the solution's
 * evaluation (null without a solution) and the action that led here, e.g. "Generated instance (Random)". Problems
 * and solutions of the framework are immutable, so a state is a snapshot that undo and redo can return to.
 */
public record LabState<S, P>(P problem, S solution, Evaluation evaluation, String action) {

    public boolean hasSolution() {
        return solution != null;
    }

    // true if both states have the same instance object, so that IDs and solutions can be compared
    public boolean sameInstance(LabState<S, P> other) {
        return other != null && other.problem == problem;
    }
}
