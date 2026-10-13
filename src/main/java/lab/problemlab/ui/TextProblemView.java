package lab.problemlab.ui;

import base.OptimizationProblem;
import lab.problemlab.ProblemWorkbench;
import lab.problemlab.model.LabState;
import representation.base.Representation;

import javax.swing.*;
import java.awt.*;

/**
 * The view of problems without one of their own: the instance and the solution as text. Read only; solutions still
 * change through the generators and mutation operators of the side panel.
 */
public final class TextProblemView<S extends Representation<S>, P extends OptimizationProblem<S>> implements ProblemView<S, P> {

    private final ProblemWorkbench<S, P> workbench;
    private final JTextArea text = new JTextArea();
    private final JScrollPane scroll = new JScrollPane(text);

    public TextProblemView(ProblemWorkbench<S, P> workbench) {
        this.workbench = workbench;
        text.setEditable(false);
        text.setLineWrap(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        text.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    @Override
    public JComponent component() {
        return scroll;
    }

    @Override
    public void stateChanged(LabState<S, P> previous, LabState<S, P> current) {
        if (current == null) {
            text.setText("Generate or open an instance.");
            return;
        }
        text.setText("Instance: " + workbench.describe(current.problem()) + "\n\n"
                + (current.hasSolution() ? "Solution:\n" + current.solution() : "No solution yet."));
        text.setCaretPosition(0);
    }
}
