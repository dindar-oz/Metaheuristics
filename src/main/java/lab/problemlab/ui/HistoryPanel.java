package lab.problemlab.ui;

import lab.problemlab.model.Evaluation;
import lab.problemlab.model.LabState;
import lab.problemlab.model.Workspace;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.List;

/**
 * The session history: a plot of the cost after every action and a table of the actions. Double-clicking a row (or
 * pressing Enter), or clicking the plot, returns to that state; states after the current one can be redone and are
 * shown in gray.
 */
final class HistoryPanel extends JSplitPane {

    private static final Color INFEASIBLE = new Color(205, 40, 40);
    private static final Color LINE = new Color(40, 90, 170);

    private final Workspace<?, ?> workspace;
    private final Model model = new Model();
    private final JTable table = new JTable(model);
    private final Trace trace = new Trace();

    HistoryPanel(Workspace<?, ?> workspace) {
        super(HORIZONTAL_SPLIT);
        this.workspace = workspace;
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new Renderer());
        int[] widths = {40, 330, 110, 100, 70};
        for (int c = 0; c < widths.length; c++)
            table.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() >= 0)
                    workspace.goTo(table.getSelectedRow());
            }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "goTo");
        table.getActionMap().put("goTo", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (table.getSelectedRow() >= 0)
                    workspace.goTo(table.getSelectedRow());
            }
        });

        JPanel tracePanel = new JPanel(new BorderLayout());
        tracePanel.add(new JLabel(" Cost after each action (click to go back)"), BorderLayout.NORTH);
        tracePanel.add(trace, BorderLayout.CENTER);
        setLeftComponent(tracePanel);
        setRightComponent(new JScrollPane(table));
        setResizeWeight(0.4);
        setDividerLocation(380);
    }

    void refresh() {
        model.fireTableDataChanged();
        int current = workspace.currentIndex();
        if (current >= 0) {
            table.setRowSelectionInterval(current, current);
            table.scrollRectToVisible(table.getCellRect(current, 0, true));
        }
        trace.repaint();
    }

    private List<? extends LabState<?, ?>> history() {
        return workspace.history();
    }

    // the change of cost from the previous state, if both have a solution of the same instance
    private Double delta(int index) {
        if (index == 0)
            return null;
        LabState<?, ?> state = history().get(index), before = history().get(index - 1);
        if (state.evaluation() == null || before.evaluation() == null)
            return null;
        return state.evaluation().cost() - before.evaluation().cost();
    }

    private final class Model extends AbstractTableModel {
        private final String[] columns = {"#", "Action", "Cost", "Δ", "Feasible"};

        @Override
        public int getRowCount() {
            return history().size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            LabState<?, ?> state = history().get(row);
            Evaluation e = state.evaluation();
            return switch (column) {
                case 0 -> (row == workspace.currentIndex() ? "▶ " : "") + (row + 1);
                case 1 -> state.action();
                case 2 -> e == null ? "–" : EvaluationPanel.format(e.cost());
                case 3 -> {
                    Double d = delta(row);
                    yield d == null || d == 0 ? "" : (d > 0 ? "+" : "") + EvaluationPanel.format(d);
                }
                default -> e == null ? "" : e.feasible() ? "yes" : "no";
            };
        }
    }

    private final class Renderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            boolean future = row > workspace.currentIndex();
            setFont(getFont().deriveFont(row == workspace.currentIndex() ? Font.BOLD : future ? Font.ITALIC : Font.PLAIN));
            setHorizontalAlignment(column >= 2 && column <= 3 ? RIGHT : LEFT);
            if (!selected) {
                Double d = column == 3 ? delta(row) : null;
                setForeground(future ? Color.GRAY : d == null ? Color.BLACK : d > 0 ? INFEASIBLE : new Color(30, 125, 50));
                if (column == 4 && "no".equals(value))
                    setForeground(INFEASIBLE);
            }
            return this;
        }
    }

    /**
     * The cost of every state with a solution against its position in the history. Infeasible states are red dots;
     * the current state is marked by a vertical line.
     */
    private final class Trace extends JComponent {
        private static final int PAD = 10;

        Trace() {
            setPreferredSize(new Dimension(300, 120));
            setToolTipText("");
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    int index = indexAt(e.getX());
                    if (index >= 0)
                        workspace.goTo(index);
                }
            });
        }

        private double x(int index) {
            int n = history().size();
            return n <= 1 ? getWidth() / 2.0 : PAD + index * (getWidth() - 2.0 * PAD) / (n - 1);
        }

        private int indexAt(int px) {
            int n = history().size();
            if (n == 0)
                return -1;
            int index = (int) Math.round((px - PAD) * (n - 1) / Math.max(1.0, getWidth() - 2.0 * PAD));
            return Math.max(0, Math.min(n - 1, index));
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int index = indexAt(event.getX());
            return index < 0 ? null : (index + 1) + ": " + history().get(index);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, getWidth(), getHeight());
            List<? extends LabState<?, ?>> states = history();
            double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
            for (LabState<?, ?> s : states)
                if (s.evaluation() != null) {
                    min = Math.min(min, s.evaluation().cost());
                    max = Math.max(max, s.evaluation().cost());
                }
            int current = workspace.currentIndex();
            if (current >= 0) {
                g.setColor(new Color(200, 200, 200));
                g.draw(new Line2D.Double(x(current), 0, x(current), getHeight()));
            }
            if (min > max) {
                g.setColor(Color.GRAY);
                g.drawString("no solutions yet", PAD, getHeight() / 2);
                g.dispose();
                return;
            }
            double range = max > min ? max - min : 1;
            double height = getHeight() - 2.0 * PAD;
            Path2D.Double path = new Path2D.Double();
            boolean drawing = false;
            for (int k = 0; k < states.size(); k++) {
                Evaluation e = states.get(k).evaluation();
                if (e == null) {
                    drawing = false;    // a gap where there was no solution
                    continue;
                }
                double y = PAD + height - (e.cost() - min) / range * height;
                if (drawing)
                    path.lineTo(x(k), y);
                else
                    path.moveTo(x(k), y);
                drawing = true;
            }
            g.setColor(LINE);
            g.setStroke(new BasicStroke(1.6f));
            g.draw(path);
            for (int k = 0; k < states.size(); k++) {
                Evaluation e = states.get(k).evaluation();
                if (e == null)
                    continue;
                double y = PAD + height - (e.cost() - min) / range * height;
                double r = k == current ? 4.5 : 2.5;
                g.setColor(k > current ? Color.LIGHT_GRAY : e.feasible() ? LINE : INFEASIBLE);
                g.fill(new Ellipse2D.Double(x(k) - r, y - r, 2 * r, 2 * r));
            }
            g.setColor(Color.DARK_GRAY);
            g.setFont(g.getFont().deriveFont(10f));
            g.drawString(EvaluationPanel.format(max), 2, 10);
            g.drawString(EvaluationPanel.format(min), 2, getHeight() - 2);
            g.dispose();
        }
    }
}
