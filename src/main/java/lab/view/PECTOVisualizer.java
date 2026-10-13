package lab.view;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;
import problems.pecto.Urgency;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Draws a PECTO solution on the map of the disaster area: ambulances (squares), patients colored by urgency, hospitals
 * (red crosses) and the route of every ambulance in its own color. The table lists the routes with their costs;
 * selecting a row, or clicking an ambulance or a patient, highlights one route with its direction, trip numbers and
 * dashed pickup legs. Hover over a participant for its details. Constraint violations (Eqs. 12-13) are reported and
 * marked on the map.
 */
public final class PECTOVisualizer implements SolutionVisualizer<PECTOSolution, PECTOProblem> {

    private static final Color AMBULANCE = new Color(40, 55, 80);
    private static final Color HOSPITAL = new Color(215, 35, 35);
    private static final Color VIOLATION = new Color(200, 0, 0);

    @Override
    public JComponent view(PECTOProblem problem, PECTOSolution solution) {
        return new SolutionPanel(new Facts(problem, solution));
    }

    // red for critical patients through blue for stable ones
    public static Color urgencyColor(Urgency u) {
        switch (u) {
            case CRITICAL:
                return new Color(178, 24, 43);
            case HEAVY:
                return new Color(239, 125, 60);
            case MODERATE:
                return new Color(235, 190, 40);
            case LIGHT:
                return new Color(103, 169, 207);
            default:
                return new Color(33, 102, 172);
        }
    }

    // well separated hues for consecutive ambulances (golden angle)
    public static Color routeColor(int ambulance) {
        return Color.getHSBColor((float) ((ambulance * 0.381966) % 1.0), 0.8f, 0.75f);
    }

    // a red cross in a white circle; a thick ring when over capacity
    public static void hospitalSymbol(Graphics2D g, double x, double y, double r, boolean over) {
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setColor(over ? VIOLATION : HOSPITAL);
        g.setStroke(new BasicStroke(over ? 3f : 1.5f));
        g.draw(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
        g.setStroke(new BasicStroke((float) (r / 2.5)));
        g.draw(new Line2D.Double(x - r * 0.55, y, x + r * 0.55, y));
        g.draw(new Line2D.Double(x, y - r * 0.55, x, y + r * 0.55));
    }

    /**
     * What the views show, computed once: who transports each patient, the route costs and the violations.
     */
    private static final class Facts {
        final PECTOProblem problem;
        final PECTOSolution solution;
        final int[] carrier;        // by patient index: the ambulance of the (last) trip to the patient, -1 if none
        final int[] trip;           // by patient index: that trip's index in the route
        final int[] visits;         // by patient index: number of trips to the patient
        final double[] routeCost;   // by ambulance
        final double cost;
        final int missing, duplicated, overfull;

        Facts(PECTOProblem problem, PECTOSolution solution) {
            this.problem = problem;
            this.solution = solution;
            int patients = problem.patientCount();
            carrier = new int[patients];
            trip = new int[patients];
            visits = new int[patients];
            Arrays.fill(carrier, -1);
            routeCost = new double[problem.ambulanceCount()];
            for (int a = 0; a < routeCost.length; a++) {
                Route route = solution.route(a);
                routeCost[a] = problem.routeCost(route);
                for (int t = 0; t < route.tripCount(); t++) {
                    int p = problem.index(route.patient(t));
                    carrier[p] = a;
                    trip[p] = t;
                    visits[p]++;
                }
            }
            cost = problem.cost(solution);
            int m = 0, d = 0, o = 0;
            for (int v : visits) {
                m += v == 0 ? 1 : 0;
                d += v > 1 ? 1 : 0;
            }
            for (int k = 0; k < problem.hospitalCount(); k++)
                o += problem.room(solution, problem.hospitalId(k)) < 0 ? 1 : 0;
            missing = m;
            duplicated = d;
            overfull = o;
        }
    }

    private static final class SolutionPanel extends JPanel {
        private final Facts facts;
        private final MapCanvas canvas;
        private final JTable table;

        SolutionPanel(Facts facts) {
            super(new BorderLayout(0, 6));
            this.facts = facts;
            PECTOProblem problem = facts.problem;
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            int busy = 0, longest = 0;
            for (Route r : facts.solution.routes()) {
                busy += r.isEmpty() ? 0 : 1;
                longest = Math.max(longest, r.tripCount());
            }
            List<String> violations = new ArrayList<>();
            if (facts.missing > 0)
                violations.add(facts.missing + " patient(s) not transported");
            if (facts.duplicated > 0)
                violations.add(facts.duplicated + " patient(s) transported more than once");
            if (facts.overfull > 0)
                violations.add(facts.overfull + " hospital(s) over capacity");
            String status = violations.isEmpty() ? "<font color='#2a7a2a'>feasible</font>"
                    : "<font color='red'>infeasible: " + String.join(", ", violations) + "</font>";
            JLabel summary = new JLabel(String.format("<html><b>%d ambulances, %d patients, %d hospitals</b> &nbsp; cost %.2f &nbsp; %s"
                            + "<br>%d trips by %d ambulances (at most %d by one) &nbsp; beds used %d of %d</html>",
                    problem.ambulanceCount(), problem.patientCount(), problem.hospitalCount(), facts.cost, status,
                    facts.solution.tripCount(), busy, longest, facts.solution.tripCount(), problem.totalCapacity()));

            canvas = new MapCanvas(facts);
            table = routeTable();

            JCheckBox allRoutes = new JCheckBox("All routes", true);
            allRoutes.addActionListener(e -> canvas.setAllRoutes(allRoutes.isSelected()));
            JCheckBox labels = new JCheckBox("IDs", problem.participantCount() <= 150);
            labels.addActionListener(e -> canvas.setLabels(labels.isSelected()));
            canvas.setLabels(labels.isSelected());
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            controls.add(allRoutes);
            controls.add(labels);
            controls.add(new JLabel("click an ambulance, a patient or a row to follow a route; click elsewhere to see all"));

            JPanel top = new JPanel(new BorderLayout(0, 4));
            top.add(summary, BorderLayout.NORTH);
            top.add(controls, BorderLayout.SOUTH);

            JScrollPane tableScroll = new JScrollPane(table);
            tableScroll.setPreferredSize(new Dimension(275, 400));
            JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, canvas, tableScroll);
            split.setResizeWeight(1);
            add(top, BorderLayout.NORTH);
            add(split, BorderLayout.CENTER);

            canvas.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    select(canvas.routeAt(e.getPoint()));
                }
            });
        }

        private JTable routeTable() {
            double total = facts.cost;
            AbstractTableModel model = new AbstractTableModel() {
                private final String[] columns = {"Ambulance", "Trips", "Cost", "Share %"};

                @Override
                public int getRowCount() {
                    return facts.routeCost.length;
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
                public Class<?> getColumnClass(int column) {
                    return column < 2 ? Integer.class : Double.class;
                }

                @Override
                public Object getValueAt(int row, int column) {
                    switch (column) {
                        case 0:
                            return row;
                        case 1:
                            return facts.solution.route(row).tripCount();
                        case 2:
                            return facts.routeCost[row];
                        default:
                            return total > 0 ? 100 * facts.routeCost[row] / total : 0.0;
                    }
                }
            };
            JTable t = new JTable(model);
            t.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            t.setDefaultRenderer(Double.class, new DefaultTableCellRenderer() {
                @Override
                protected void setValue(Object value) {
                    setHorizontalAlignment(RIGHT);
                    setText(String.format("%.1f", (Double) value));
                }
            });
            int[] widths = {80, 45, 70, 60};
            for (int c = 0; c < widths.length; c++)
                t.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
            TableRowSorter<AbstractTableModel> sorter = new TableRowSorter<>(model);
            sorter.setSortKeys(Collections.singletonList(new RowSorter.SortKey(2, SortOrder.DESCENDING)));
            t.setRowSorter(sorter);
            t.getSelectionModel().addListSelectionListener(e -> {
                int row = t.getSelectedRow();
                canvas.setSelected(row < 0 ? -1 : t.convertRowIndexToModel(row));
            });
            return t;
        }

        // highlights the route of the ambulance and shows it in the table (-1: no route highlighted)
        private void select(int ambulance) {
            if (ambulance < 0) {
                table.clearSelection();
                return;
            }
            int row = table.convertRowIndexToView(ambulance);
            table.setRowSelectionInterval(row, row);
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
    }

    private static final class MapCanvas extends JPanel {
        private static final int MARGIN = 24;
        private static final int LEGEND = 26;

        private final Facts facts;
        private final PECTOProblem problem;
        private final double minX, minY, range;
        private int selected = -1;
        private boolean allRoutes = true;
        private boolean labels;

        MapCanvas(Facts facts) {
            this.facts = facts;
            this.problem = facts.problem;
            double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (int id = 0; id < problem.participantCount(); id++) {
                x0 = Math.min(x0, problem.x(id));
                x1 = Math.max(x1, problem.x(id));
                y0 = Math.min(y0, problem.y(id));
                y1 = Math.max(y1, problem.y(id));
            }
            minX = x0;
            minY = y0;
            range = Math.max(Math.max(x1 - x0, y1 - y0), 1e-9);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(760, 680));
            setToolTipText("");
        }

        void setSelected(int ambulance) {
            selected = ambulance;
            repaint();
        }

        void setAllRoutes(boolean allRoutes) {
            this.allRoutes = allRoutes;
            repaint();
        }

        void setLabels(boolean labels) {
            this.labels = labels;
            repaint();
        }

        // one scale for both axes keeps distances true; y grows upwards
        private double scale() {
            return Math.max(1e-9, Math.min(getWidth() - 2.0 * MARGIN, getHeight() - 2.0 * MARGIN - LEGEND) / range);
        }

        private double px(int id) {
            double s = scale();
            return (getWidth() - range * s) / 2 + (problem.x(id) - minX) * s;
        }

        private double py(int id) {
            double s = scale();
            return (getHeight() - LEGEND + range * s) / 2 - (problem.y(id) - minY) * s;
        }

        private double patientRadius() {
            int n = problem.patientCount();
            return n > 2000 ? 1.8 : n > 500 ? 2.5 : 3.5;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(g.getFont().deriveFont(10f));

            double s = scale();
            g.setColor(new Color(235, 235, 235));
            g.draw(new Rectangle2D.Double((getWidth() - range * s) / 2, (getHeight() - LEGEND - range * s) / 2, range * s, range * s));

            if (allRoutes) {
                boolean dim = selected >= 0;
                g.setStroke(new BasicStroke(problem.patientCount() > 1000 ? 0.7f : 1.1f));
                for (int a = 0; a < problem.ambulanceCount(); a++)
                    if (a != selected) {
                        Color c = dim ? new Color(170, 170, 170) : routeColor(a);
                        g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), dim ? 60 : 110));
                        g.draw(path(facts.solution.route(a)));
                    }
            }
            drawPatients(g);
            drawHospitals(g);
            drawAmbulances(g);
            if (selected >= 0)
                drawSelectedRoute(g, facts.solution.route(selected));
            drawLegend(g);
            g.dispose();
        }

        private Path2D path(Route route) {
            Path2D.Double path = new Path2D.Double();
            path.moveTo(px(route.ambulance()), py(route.ambulance()));
            for (int t = 0; t < route.tripCount(); t++) {
                path.lineTo(px(route.patient(t)), py(route.patient(t)));
                path.lineTo(px(route.hospital(t)), py(route.hospital(t)));
            }
            return path;
        }

        private void drawPatients(Graphics2D g) {
            double r = patientRadius();
            for (int k = 0; k < problem.patientCount(); k++) {
                int id = problem.patientId(k);
                Shape dot = new Ellipse2D.Double(px(id) - r, py(id) - r, 2 * r, 2 * r);
                g.setColor(urgencyColor(problem.urgency(id)));
                g.fill(dot);
                if (facts.visits[k] != 1) {     // not transported, or more than once
                    g.setColor(VIOLATION);
                    g.setStroke(new BasicStroke(1.5f));
                    g.draw(new Ellipse2D.Double(px(id) - r - 3, py(id) - r - 3, 2 * r + 6, 2 * r + 6));
                }
                label(g, id, r);
            }
        }

        private void drawHospitals(Graphics2D g) {
            double r = problem.hospitalCount() > 40 ? 6 : 8;
            for (int k = 0; k < problem.hospitalCount(); k++) {
                int id = problem.hospitalId(k);
                hospitalSymbol(g, px(id), py(id), r, problem.room(facts.solution, id) < 0);
                label(g, id, r);
            }
        }

        private void drawAmbulances(Graphics2D g) {
            double r = problem.ambulanceCount() > 100 ? 3 : 4.5;
            for (int a = 0; a < problem.ambulanceCount(); a++) {
                double size = a == selected ? r + 2.5 : r;
                g.setColor(a == selected ? routeColor(a).darker() : AMBULANCE);
                g.fill(new Rectangle2D.Double(px(a) - size, py(a) - size, 2 * size, 2 * size));
                label(g, a, size);
            }
        }

        // the selected route on top: thick, with arrows, dashed legs to the patients and trip numbers
        private void drawSelectedRoute(Graphics2D g, Route route) {
            Color color = routeColor(route.ambulance()).darker();
            Stroke pickup = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 4f}, 0f);
            Stroke delivery = new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            int from = route.ambulance();
            for (int t = 0; t < route.tripCount(); t++) {
                int patient = route.patient(t), hospital = route.hospital(t);
                g.setColor(color);
                leg(g, from, patient, pickup);
                leg(g, patient, hospital, delivery);
                g.setColor(Color.BLACK);
                g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
                g.drawString(String.valueOf(t + 1), (float) (px(patient) + 5), (float) (py(patient) + 12));
                from = hospital;
            }
        }

        private void leg(Graphics2D g, int from, int to, Stroke stroke) {
            double x0 = px(from), y0 = py(from), x1 = px(to), y1 = py(to);
            g.setStroke(stroke);
            g.draw(new Line2D.Double(x0, y0, x1, y1));
            double length = Math.hypot(x1 - x0, y1 - y0);
            if (length < 14)
                return;
            // arrowhead at 60% of the leg
            double ux = (x1 - x0) / length, uy = (y1 - y0) / length;
            double ax = x0 + 0.6 * (x1 - x0), ay = y0 + 0.6 * (y1 - y0);
            Path2D.Double head = new Path2D.Double();
            head.moveTo(ax + 5 * ux, ay + 5 * uy);
            head.lineTo(ax - 5 * ux - 4 * uy, ay - 5 * uy + 4 * ux);
            head.lineTo(ax - 5 * ux + 4 * uy, ay - 5 * uy - 4 * ux);
            head.closePath();
            g.fill(head);
        }

        private void label(Graphics2D g, int id, double r) {
            if (!labels)
                return;
            g.setColor(Color.DARK_GRAY);
            g.drawString(String.valueOf(id), (float) (px(id) + r + 1), (float) (py(id) - r));
        }

        private void drawLegend(Graphics2D g) {
            int y = getHeight() - 10;
            float x = MARGIN;
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
            g.setColor(AMBULANCE);
            g.fill(new Rectangle2D.Double(x, y - 8, 8, 8));
            g.setColor(Color.DARK_GRAY);
            g.drawString("ambulance", x + 12, y);
            x += 85;
            hospitalSymbol(g, x + 5, y - 4, 6, false);
            g.setColor(Color.DARK_GRAY);
            g.drawString("hospital", x + 14, y);
            x += 75;
            g.drawString("patients:", x, y);
            x += 55;
            Urgency[] levels = Urgency.values();
            for (int k = levels.length - 1; k >= 0; k--) {
                g.setColor(urgencyColor(levels[k]));
                g.fill(new Ellipse2D.Double(x, y - 8, 8, 8));
                g.setColor(Color.DARK_GRAY);
                String name = levels[k].name().toLowerCase();
                g.drawString(name, x + 11, y);
                x += 18 + g.getFontMetrics().stringWidth(name);
            }
            g.setColor(VIOLATION);
            g.setStroke(new BasicStroke(1.5f));
            g.draw(new Ellipse2D.Double(x, y - 9, 10, 10));
            g.setColor(Color.DARK_GRAY);
            g.drawString("violates Eq. 12/13", x + 14, y);
        }

        // the participant under the point: hospitals first, then ambulances, then patients (-1 if none)
        private int participantAt(Point p) {
            int[][] groups = {
                    {problem.hospitalId(0), problem.hospitalCount(), 9},
                    {problem.ambulanceId(0), problem.ambulanceCount(), 6},
                    {problem.patientId(0), problem.patientCount(), 5}};
            for (int[] group : groups) {
                int best = -1;
                double bestDistance = group[2];
                for (int id = group[0]; id < group[0] + group[1]; id++) {
                    double d = p.distance(px(id), py(id));
                    if (d <= bestDistance) {
                        bestDistance = d;
                        best = id;
                    }
                }
                if (best >= 0)
                    return best;
            }
            return -1;
        }

        // the ambulance whose route a click on the point selects (-1: none)
        int routeAt(Point p) {
            int id = participantAt(p);
            if (problem.isAmbulance(id))
                return id;
            if (problem.isPatient(id))
                return facts.carrier[problem.index(id)];
            return -1;
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            int id = participantAt(event.getPoint());
            if (id < 0)
                return null;
            String where = String.format("at (%.1f, %.1f)", problem.x(id), problem.y(id));
            switch (problem.typeOf(id)) {
                case AMBULANCE: {
                    Route route = facts.solution.route(id);
                    return String.format("<html><b>Ambulance %d</b> %s<br>%d trips, route cost %.2f (%.1f%% of the total)</html>",
                            id, where, route.tripCount(), facts.routeCost[id], facts.cost > 0 ? 100 * facts.routeCost[id] / facts.cost : 0);
                }
                case PATIENT: {
                    int k = problem.index(id);
                    String transport;
                    if (facts.visits[k] == 0)
                        transport = "<font color='red'>not transported</font>";
                    else {
                        Route route = facts.solution.route(facts.carrier[k]);
                        int t = facts.trip[k];
                        int from = t == 0 ? route.ambulance() : route.hospital(t - 1);
                        transport = String.format("trip %d of ambulance %d, to hospital %d<br>trip cost %.2f",
                                t + 1, route.ambulance(), route.hospital(t), problem.tripCost(from, id, route.hospital(t)));
                        if (facts.visits[k] > 1)
                            transport += String.format("<br><font color='red'>transported %d times</font>", facts.visits[k]);
                    }
                    return String.format("<html><b>Patient %d</b> %s<br>urgency %s (weight %.2f)<br>%s</html>",
                            id, where, problem.urgency(id).name().toLowerCase(), problem.urgencyWeight(id), transport);
                }
                default: {
                    int load = facts.solution.load(id), capacity = problem.capacity(id);
                    return String.format("<html><b>Hospital %d</b> %s<br>%d of %d beds taken%s</html>", id, where, load, capacity,
                            load > capacity ? " <font color='red'>(over capacity)</font>" : "");
                }
            }
        }
    }

    @Override
    public String toString() {
        return "PECTO routes";
    }
}
