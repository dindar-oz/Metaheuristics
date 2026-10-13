package lab.problemlab.ui;

import lab.problemlab.model.Evaluation;

import javax.swing.*;
import java.awt.*;
import java.util.stream.Collectors;

/**
 * The strip above the problem view: the cost of the current solution, how it changed with the last action, whether
 * it is feasible (and why not), and the problem's own measures.
 */
final class EvaluationPanel extends JPanel {

    private static final Color GOOD = new Color(30, 125, 50);
    private static final Color BAD = new Color(190, 30, 30);

    private final JLabel cost = new JLabel();
    private final JLabel delta = new JLabel();
    private final JLabel feasibility = new JLabel();
    private final JLabel details = new JLabel();

    EvaluationPanel() {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(220, 220, 220)),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        cost.setFont(cost.getFont().deriveFont(Font.BOLD, 20f));
        delta.setFont(delta.getFont().deriveFont(Font.BOLD, 13f));
        feasibility.setFont(feasibility.getFont().deriveFont(Font.BOLD, 12f));
        feasibility.setOpaque(true);
        feasibility.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        left.add(cost);
        left.add(delta);
        left.add(feasibility);
        add(left, BorderLayout.WEST);
        add(details, BorderLayout.CENTER);
        show(null, null);
    }

    // previous: the evaluation before the last action, to show the change (null if there is none to compare)
    void show(Evaluation current, Evaluation previous) {
        if (current == null) {
            cost.setText("no solution");
            cost.setForeground(Color.GRAY);
            delta.setText("");
            feasibility.setVisible(false);
            details.setText("");
            return;
        }
        cost.setForeground(Color.BLACK);
        cost.setText("cost " + format(current.cost()));
        cost.setToolTipText(String.valueOf(current.cost()));

        double change = previous == null ? 0 : current.cost() - previous.cost();
        delta.setText(previous == null || change == 0 ? "" : (change > 0 ? "▲ +" : "▼ ") + format(change));
        delta.setForeground(change > 0 ? BAD : GOOD);
        delta.setToolTipText(previous == null ? null : "change caused by the last action (was " + format(previous.cost()) + ")");

        feasibility.setVisible(true);
        feasibility.setText(current.feasible() ? "feasible" : "infeasible");
        feasibility.setBackground(current.feasible() ? new Color(220, 242, 222) : new Color(253, 222, 222));
        feasibility.setForeground(current.feasible() ? GOOD : BAD);

        String metrics = current.metrics().entrySet().stream()
                .map(e -> "<b>" + e.getKey() + "</b> " + e.getValue())
                .collect(Collectors.joining(" &nbsp;·&nbsp; "));
        String violations = current.violations().isEmpty() ? ""
                : "<br><font color='#be1e1e'>" + String.join("; ", current.violations()) + "</font>";
        details.setText("<html>" + metrics + violations + "</html>");
    }

    static String format(double value) {
        double a = Math.abs(value);
        if (a != 0 && (a < 1e-3 || a >= 1e7))
            return String.format("%.4g", value);
        return String.format(a >= 100 ? "%,.2f" : "%.4f", value);
    }
}
