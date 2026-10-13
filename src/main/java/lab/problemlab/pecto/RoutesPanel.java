package lab.problemlab.pecto;

import lab.view.PECTOVisualizer;
import problems.pecto.PECTOProblem;
import problems.pecto.Route;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.Comparator;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Tables of the solution: every route with its cost and the change the last action made to it, the trips of the
 * highlighted route (whose hospitals can be changed in place and whose order can be changed with the buttons), the
 * hospitals with their beds, and the patients nobody transports. Selecting a row selects the participant on the map.
 */
final class RoutesPanel extends JTabbedPane {

    private static final Color WORSE = new Color(190, 30, 30);
    private static final Color BETTER = new Color(30, 125, 50);

    private final PECTOEditor editor;
    private PECTOFacts facts;
    private Double[] delta;               // by ambulance: the change of the route's cost, null if unchanged
    private int route = -1;
    private boolean updating;             // set while selections are made here, so that they are not reported back

    private final RouteModel routeModel = new RouteModel();
    private final JTable routes = new JTable(routeModel);
    private final TripModel tripModel = new TripModel();
    private final JTable trips = new TripTable(tripModel);
    private final JLabel tripsTitle = new JLabel();
    private final HospitalModel hospitalModel = new HospitalModel();
    private final JTable hospitals = new JTable(hospitalModel);
    private final DefaultListModel<Integer> missing = new DefaultListModel<>();
    private final JList<Integer> missingList = new JList<>(missing);

    RoutesPanel(PECTOEditor editor) {
        this.editor = editor;
        addTab("Routes", buildRoutesTab());
        addTab("Hospitals", buildHospitalsTab());
        addTab("Not transported", buildMissingTab());
    }

    private JComponent buildRoutesTab() {
        routes.setAutoCreateRowSorter(true);
        routes.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        routes.setDefaultRenderer(Object.class, new RouteRenderer());
        routes.setDefaultRenderer(Double.class, new RouteRenderer());
        routes.setDefaultRenderer(Integer.class, new RouteRenderer());
        onSelect(routes, row -> editor.selectRoute(row));
        int[] routeWidths = {70, 40, 70, 55, 70};
        for (int c = 0; c < routeWidths.length; c++)
            routes.getColumnModel().getColumn(c).setPreferredWidth(routeWidths[c]);

        trips.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        trips.setRowHeight(22);
        trips.setDefaultRenderer(Double.class, number("%.2f"));
        onSelect(trips, row -> editor.select(facts.solution.route(route).patient(row)));
        int[] widths = {30, 60, 80, 110, 70};
        for (int c = 0; c < widths.length; c++)
            trips.getColumnModel().getColumn(c).setPreferredWidth(widths[c]);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 0));
        buttons.add(button("▲", "Move the selected trip earlier [ [ ]", () -> withTrip(p -> editor.shiftTrip(p, -1))));
        buttons.add(button("▼", "Move the selected trip later [ ] ]", () -> withTrip(p -> editor.shiftTrip(p, 1))));
        buttons.add(button("Take out", "Take the selected trip out of the route [U]", () -> withTrip(editor::unassign)));
        buttons.add(button("Reverse", "Make the trips in the opposite order", () -> {
            if (route >= 0)
                editor.reverseRoute(route);
        }));
        buttons.add(button("Clear", "Take all trips out of the route", () -> {
            if (route >= 0)
                editor.clearRoute(route);
        }));

        JPanel tripPanel = new JPanel(new BorderLayout(0, 3));
        tripsTitle.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        tripPanel.add(tripsTitle, BorderLayout.NORTH);
        tripPanel.add(new JScrollPane(trips), BorderLayout.CENTER);
        tripPanel.add(buttons, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(routes), tripPanel);
        split.setResizeWeight(0.5);
        split.setDividerLocation(220);
        return split;
    }

    private JComponent buildHospitalsTab() {
        hospitals.setAutoCreateRowSorter(true);
        hospitals.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        hospitals.setDefaultRenderer(Integer.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(t, value, selected, focus, row, column);
                setHorizontalAlignment(RIGHT);
                if (!selected)
                    setForeground(column == 3 && (Integer) value < 0 ? WORSE : Color.BLACK);
                return this;
            }
        });
        onSelect(hospitals, row -> editor.select(facts.problem.hospitalId(row)));
        return new JScrollPane(hospitals);
    }

    private JComponent buildMissingTab() {
        missingList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                int id = (Integer) value;
                super.getListCellRendererComponent(list, "Patient %d · %s".formatted(id, PECTOEditor.name(facts.problem.urgency(id))),
                        index, selected, focus);
                setIcon(PECTOEditor.dot(PECTOCanvas.urgencyColor(facts.problem.urgency(id))));
                return this;
            }
        });
        missingList.addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && missingList.getSelectedValue() != null)
                editor.select(missingList.getSelectedValue());
        });
        JPanel panel = new JPanel(new BorderLayout(0, 4));
        panel.add(new JScrollPane(missingList), BorderLayout.CENTER);
        panel.add(new JLabel("<html><span style='color:#666'>Drag them onto a route with the route tool [R], "
                + "or press G / “Assign missing” to add them greedily.</span></html>"), BorderLayout.SOUTH);
        return panel;
    }

    // ---- updates from the editor

    void show(PECTOFacts facts, Double[] delta) {
        this.facts = facts;
        this.delta = delta;
        updating = true;
        try {
            routeModel.fireTableDataChanged();
            hospitalModel.fireTableDataChanged();
            missing.clear();
            if (facts != null && facts.hasSolution())
                for (int k = 0; k < facts.problem.patientCount(); k++)
                    if (facts.visits[k] == 0)
                        missing.addElement(facts.problem.patientId(k));
            setTitleAt(2, "Not transported" + (missing.isEmpty() ? "" : " (" + missing.size() + ")"));
            setForegroundAt(2, missing.isEmpty() ? Color.BLACK : WORSE);
        } finally {
            updating = false;
        }
        showTrips();
    }

    void setSelection(int selected, int route) {
        boolean newRoute = route != this.route;
        this.route = route;
        if (newRoute)
            showTrips();
        if (facts == null)
            return;
        updating = true;
        try {
            PECTOProblem p = facts.problem;
            select(routes, route >= 0 && facts.hasSolution() ? route : -1);
            select(hospitals, selected >= 0 && p.isHospital(selected) ? p.index(selected) : -1);
            int trip = selected >= 0 && p.isPatient(selected) && facts.carrierOf(selected) == route ? facts.tripOf(selected) : -1;
            select(trips, trip);
            if (selected >= 0 && missing.contains(selected))
                missingList.setSelectedValue(selected, true);
            else
                missingList.clearSelection();
        } finally {
            updating = false;
        }
    }

    private void showTrips() {
        updating = true;
        try {
            if (trips.isEditing())
                trips.getCellEditor().cancelCellEditing();
            tripModel.fireTableDataChanged();
        } finally {
            updating = false;
        }
        if (facts == null || !facts.hasSolution() || route < 0) {
            tripsTitle.setText("Trips: select a route");
            tripsTitle.setIcon(null);
        } else {
            Route r = facts.solution.route(route);
            tripsTitle.setText("<html><b>Trips of ambulance %d</b> · %d trips · cost %.2f</html>".formatted(route, r.tripCount(),
                    facts.routeCost[route]));
            tripsTitle.setIcon(PECTOEditor.dot(PECTOVisualizer.routeColor(route)));
        }
    }

    private void withTrip(IntConsumer action) {
        int row = trips.getSelectedRow();
        if (row >= 0 && route >= 0)
            action.accept(facts.solution.route(route).patient(row));
        else
            editor.host().status("Select a trip first");
    }

    // ---- table helpers

    // selects a model row of the table, or clears the selection (-1)
    private static void select(JTable table, int modelRow) {
        if (modelRow < 0 || modelRow >= table.getModel().getRowCount()) {
            table.clearSelection();
            return;
        }
        int row = table.convertRowIndexToView(modelRow);
        table.setRowSelectionInterval(row, row);
        table.scrollRectToVisible(table.getCellRect(row, 0, true));
    }

    // reports the model row the user selects
    private void onSelect(JTable table, IntConsumer action) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!updating && !e.getValueIsAdjusting() && table.getSelectedRow() >= 0 && facts != null)
                action.accept(table.convertRowIndexToModel(table.getSelectedRow()));
        });
    }

    private static DefaultTableCellRenderer number(String format) {
        return new DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                setHorizontalAlignment(RIGHT);
                setText(value == null ? "" : String.format(format, (Double) value));
            }
        };
    }

    private static JButton button(String text, String tooltip, Runnable body) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.addActionListener(e -> body.run());
        return b;
    }

    // ---- models

    private final class RouteModel extends AbstractTableModel {
        private final String[] columns = {"Ambulance", "Trips", "Cost", "Share %", "Δ last"};

        @Override
        public int getRowCount() {
            return facts == null || !facts.hasSolution() ? 0 : facts.problem.ambulanceCount();
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
            return switch (column) {
                case 0 -> row;
                case 1 -> facts.solution.route(row).tripCount();
                case 2 -> facts.routeCost[row];
                case 3 -> facts.cost > 0 ? 100 * facts.routeCost[row] / facts.cost : 0.0;
                default -> delta == null ? null : delta[row];
            };
        }
    }

    // changed routes in bold, cost changes in red (worse) or green (better)
    private final class RouteRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(t, value, selected, focus, row, column);
            int ambulance = t.convertRowIndexToModel(row);
            Double change = delta == null ? null : delta[ambulance];
            setFont(getFont().deriveFont(change != null ? Font.BOLD : Font.PLAIN));
            setHorizontalAlignment(column == 0 ? LEFT : RIGHT);
            setIcon(column == 0 ? PECTOEditor.dot(PECTOVisualizer.routeColor(ambulance)) : null);
            if (value instanceof Double d)
                setText(column == 4 ? (d > 0 ? "+" : "") + "%.2f".formatted(d) : column == 3 ? "%.1f".formatted(d) : "%.2f".formatted(d));
            if (!selected)
                setForeground(column == 4 && change != null ? (change > 0 ? WORSE : BETTER) : Color.BLACK);
            return this;
        }
    }

    private record HospitalChoice(int id, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final class TripModel extends AbstractTableModel {
        private final String[] columns = {"#", "Patient", "Urgency", "Hospital", "Trip cost"};

        private Route current() {
            return facts == null || !facts.hasSolution() || route < 0 ? null : facts.solution.route(route);
        }

        @Override
        public int getRowCount() {
            Route r = current();
            return r == null ? 0 : r.tripCount();
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
            return column == 4 ? Double.class : Object.class;
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 3;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Route r = current();
            int patient = r.patient(row), hospital = r.hospital(row);
            return switch (column) {
                case 0 -> row + 1;
                case 1 -> patient;
                case 2 -> PECTOEditor.name(facts.problem.urgency(patient));
                case 3 -> hospital + "  (" + facts.load(hospital) + "/" + facts.problem.capacity(hospital) + ")";
                default -> facts.tripCost(r, row);
            };
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            if (value instanceof HospitalChoice choice)
                editor.deliver(current().patient(row), choice.id());
        }
    }

    // the hospital column is edited with a list of hospitals, nearest to the patient first
    private final class TripTable extends JTable {
        TripTable(TripModel model) {
            super(model);
        }

        @Override
        public TableCellEditor getCellEditor(int row, int column) {
            if (column != 3)
                return super.getCellEditor(row, column);
            PECTOProblem p = facts.problem;
            int patient = facts.solution.route(route).patient(row);
            HospitalChoice[] choices = IntStream.range(0, p.hospitalCount()).map(p::hospitalId).boxed()
                    .sorted(Comparator.comparingDouble(h -> p.distance(patient, h)))
                    .map(h -> new HospitalChoice(h, "%d · %.1f away · %d free".formatted(h, p.distance(patient, h), p.room(facts.solution, h))))
                    .toArray(HospitalChoice[]::new);
            return new DefaultCellEditor(new JComboBox<>(choices));
        }
    }

    private final class HospitalModel extends AbstractTableModel {
        private final String[] columns = {"Hospital", "Taken", "Beds", "Free"};

        @Override
        public int getRowCount() {
            return facts == null ? 0 : facts.problem.hospitalCount();
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
            return Integer.class;
        }

        @Override
        public Object getValueAt(int row, int column) {
            int id = facts.problem.hospitalId(row), load = facts.load(id), beds = facts.problem.capacity(id);
            return switch (column) {
                case 0 -> id;
                case 1 -> load;
                case 2 -> beds;
                default -> beds - load;
            };
        }
    }
}
