package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;

import java.util.ArrayList;
import java.util.List;

/**
 * One diagnosed run as the views see it: the instance, what the ants know about it, what is practical to show, and
 * the participant selected on the map or in a table. Swing thread only.
 */
final class Session {

    final PECTOProblem problem;
    final ColonySettings settings;
    final Candidates candidates;
    final Practicality practicality;
    final RunController controller;
    private int selected = -1;
    private final List<Runnable> selectionListeners = new ArrayList<>();

    Session(RunController controller) {
        this.controller = controller;
        this.problem = controller.problem;
        this.settings = controller.settings;
        this.candidates = controller.candidates;
        this.practicality = Practicality.of(problem);
    }

    int selected() {
        return selected;
    }

    void select(int id) {
        if (id == selected)
            return;
        selected = id;
        selectionListeners.forEach(Runnable::run);
    }

    void onSelection(Runnable listener) {
        selectionListeners.add(listener);
    }

    String label(int id) {
        return Style.label(problem, id);
    }
}
