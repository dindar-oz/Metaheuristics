package lab.problemlab.ui;

import lab.problemlab.model.LabState;

import javax.swing.*;
import java.util.List;

/**
 * The problem-specific part of a lab window: shows the current instance and solution and lets the user edit them.
 * A view changes the session only through its {@link lab.problemlab.model.Workspace}, so that every edit is
 * evaluated and can be undone; the window then calls {@link #stateChanged}. All methods are called on the Swing thread.
 */
public interface ProblemView<S, P> {

    JComponent component();

    // Shows the current state; previous is the state shown before (null at first), e.g. to highlight what changed.
    // current is null until there is an instance.
    void stateChanged(LabState<S, P> previous, LabState<S, P> current);

    // problem-specific commands, offered in the window's "Problem" menu
    default List<Action> actions() {
        return List.of();
    }

    // how to use the view with the mouse and the keyboard, as HTML (null if there is nothing to explain)
    default String help() {
        return null;
    }
}
