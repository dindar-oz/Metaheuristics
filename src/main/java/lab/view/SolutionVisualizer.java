package lab.view;

import base.OptimizationProblem;
import representation.base.Representation;

import javax.swing.*;
import java.awt.*;

/**
 * Shows a solution of a problem in the lab ("View solution"). Called on the Swing thread; the returned component
 * is placed in its own window. Implementations must not modify the solution.
 */
@FunctionalInterface
public interface SolutionVisualizer<S extends Representation<S>, P extends OptimizationProblem<S>> {

    JComponent view(P problem, S solution);

    // Fallback for problems without a dedicated visualizer: the cost and the solution's toString()
    static <S extends Representation<S>, P extends OptimizationProblem<S>> SolutionVisualizer<S, P> text() {
        return (problem, solution) -> {
            JTextArea text = new JTextArea("Cost: " + problem.cost(solution)
                    + "\nFeasible: " + problem.isFeasible(solution) + "\n\n" + solution);
            text.setEditable(false);
            text.setLineWrap(true);
            text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
            text.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JScrollPane scroll = new JScrollPane(text);
            scroll.setPreferredSize(new Dimension(560, 420));
            return scroll;
        };
    }
}
