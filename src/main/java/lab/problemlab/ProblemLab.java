package lab.problemlab;

import base.OptimizationProblem;
import com.formdev.flatlaf.FlatLightLaf;
import lab.problemlab.ui.ProblemLabFrame;
import representation.base.Representation;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.Locale;

/**
 * Desktop tool to look at problem instances and solutions, edit them and watch the cost and the constraints change:
 * a lab for understanding a problem rather than for running algorithms (see {@link lab.MetaheuristicLab} for that).
 * The user picks a problem from {@link ProblemLabCatalog}, which opens a {@link ProblemLabFrame} for it.
 * <p>
 * Run with {@code ./mvnw compile exec:java -Dexec.mainClass=lab.problemlab.ProblemLab}; an argument opens the
 * problem whose name contains it directly, e.g. {@code -Dexec.args=PECTO}.
 */
public final class ProblemLab {

    private ProblemLab() {
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            String query = String.join(" ", args).strip().toLowerCase(Locale.ROOT);
            if (!query.isEmpty()) {
                for (ProblemWorkbench<?, ?> workbench : ProblemLabCatalog.workbenches())
                    if (workbench.name().toLowerCase(Locale.ROOT).contains(query)) {
                        open(workbench);
                        return;
                    }
                System.err.println("No problem matches '" + query + "'");
            }
            chooseAndOpen(null);
        });
    }

    // Lets the user pick a problem and opens a lab window for it; call on the Swing thread
    public static void chooseAndOpen(Component parent) {
        List<ProblemWorkbench<?, ?>> workbenches = ProblemLabCatalog.workbenches();
        JList<ProblemWorkbench<?, ?>> list = new JList<>(workbenches.toArray(new ProblemWorkbench<?, ?>[0]));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectedIndex(0);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
                ProblemWorkbench<?, ?> w = (ProblemWorkbench<?, ?>) value;
                super.getListCellRendererComponent(l, "<html><b>%s</b><br><div style='width:380px'>%s</div></html>"
                        .formatted(w.name(), w.description()), index, selected, focus);
                setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
                return this;
            }
        });

        JOptionPane pane = new JOptionPane(new JScrollPane(list), JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                null, new Object[]{"Open", "Cancel"}, "Open");
        JDialog dialog = pane.createDialog(parent, "Problem Lab – choose a problem");
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    pane.setValue("Open");
                    dialog.dispose();
                }
            }
        });
        dialog.setVisible(true);
        dialog.dispose();
        if ("Open".equals(pane.getValue()) && list.getSelectedValue() != null)
            open(list.getSelectedValue());
    }

    // Opens a lab window for the problem; call on the Swing thread
    public static ProblemLabFrame<?, ?> open(ProblemWorkbench<?, ?> workbench) {
        return show(workbench);
    }

    private static <S extends Representation<S>, P extends OptimizationProblem<S>> ProblemLabFrame<S, P> show(ProblemWorkbench<S, P> workbench) {
        ProblemLabFrame<S, P> frame = new ProblemLabFrame<>(workbench);
        frame.setVisible(true);
        return frame;
    }
}
