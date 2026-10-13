package lab.problemlab.pecto;

import problems.pecto.PECTOProblem;
import problems.pecto.ParticipantType;
import problems.pecto.Route;
import problems.pecto.Urgency;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * The properties of the selected participant, editable: its coordinates, a patient's urgency, a hospital's capacity,
 * and buttons for the edits that concern it. Every change is an edit made through the {@link PECTOEditor}. The
 * controls stay in place while the same participant is selected, so that spinner arrows can be held down.
 */
final class SelectionPanel extends JPanel {

    private final PECTOEditor editor;
    private final JLabel title = new JLabel();
    private final JLabel info = new JLabel();
    private final JPanel form = new JPanel(new GridBagLayout());
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JSpinner x = coordinate(), y = coordinate();
    private final JComboBox<Urgency> urgency = new JComboBox<>(Urgency.values());
    private final JSpinner capacity = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));

    private int shown = -1;                 // the participant the controls are for
    private ParticipantType shownType;
    private PECTOFacts facts;
    private boolean updating;               // set while the controls are filled in, so that no edit is made

    SelectionPanel(PECTOEditor editor) {
        super(new BorderLayout(0, 4));
        this.editor = editor;
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder("Selected"),
                BorderFactory.createEmptyBorder(0, 4, 4, 4)));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));
        add(title, BorderLayout.NORTH);
        add(form, BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout(0, 4));
        south.add(info, BorderLayout.NORTH);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        x.addChangeListener(e -> moved());
        y.addChangeListener(e -> moved());
        urgency.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                Urgency u = (Urgency) value;
                super.getListCellRendererComponent(list, u == null ? "" : "%s (weight %.1f)".formatted(PECTOEditor.name(u), u.weight()),
                        index, selected, focus);
                if (u != null)
                    setIcon(PECTOEditor.dot(PECTOCanvas.urgencyColor(u)));
                return this;
            }
        });
        urgency.addActionListener(e -> {
            if (!updating)
                editor.setUrgency(shown, (Urgency) urgency.getSelectedItem());
        });
        capacity.addChangeListener(e -> {
            if (!updating)
                editor.setCapacity(shown, (Integer) capacity.getValue());
        });
        show(null, -1);
    }

    void show(PECTOFacts facts, int id) {
        this.facts = facts;
        if (facts == null || id < 0) {
            shown = -1;
            shownType = null;
            title.setText("Nothing selected");
            form.removeAll();
            buttons.removeAll();
            info.setText(wrap("<span style='color:#777'>Click a participant on the map, or a row of a table.</span>"));
            revalidate();
            repaint();
            return;
        }
        PECTOProblem p = facts.problem;
        ParticipantType type = p.typeOf(id);
        if (id != shown || type != shownType)
            build(id, type);
        updating = true;
        try {
            x.setValue(p.x(id));
            y.setValue(p.y(id));
            if (type == ParticipantType.PATIENT)
                urgency.setSelectedItem(p.urgency(id));
            if (type == ParticipantType.HOSPITAL)
                capacity.setValue(p.capacity(id));
        } finally {
            updating = false;
        }
        info.setText(wrap(info(id, type)));
        for (Component c : buttons.getComponents())
            c.setEnabled(facts.hasSolution() || "Remove".equals(((JButton) c).getText()));
    }

    // new controls for another participant
    private void build(int id, ParticipantType type) {
        shown = id;
        shownType = type;
        title.setText(switch (type) {
            case AMBULANCE -> "Ambulance " + id;
            case PATIENT -> "Patient " + id;
            case HOSPITAL -> "Hospital " + id;
        });
        form.removeAll();
        row(0, "x", x);
        row(1, "y", y);
        if (type == ParticipantType.PATIENT)
            row(2, "urgency", urgency);
        if (type == ParticipantType.HOSPITAL)
            row(2, "beds", capacity);

        buttons.removeAll();
        switch (type) {
            case AMBULANCE -> {
                buttons.add(button("Clear route", "Take all trips out of this route", () -> editor.clearRoute(shown)));
                buttons.add(button("Reverse", "Make the trips in the opposite order", () -> editor.reverseRoute(shown)));
            }
            case PATIENT -> {
                buttons.add(button("Take out", "Take the patient's trip out of its route [U]", () -> editor.unassign(shown)));
                buttons.add(button("▲", "Earlier in its route [ [ ]", () -> editor.shiftTrip(shown, -1)));
                buttons.add(button("▼", "Later in its route [ ] ]", () -> editor.shiftTrip(shown, 1)));
            }
            case HOSPITAL -> {
            }
        }
        buttons.add(button("Remove", "Remove from the instance [Delete]", () -> editor.deleteParticipant(shown)));
        revalidate();
        repaint();
    }

    private String info(int id, ParticipantType type) {
        PECTOProblem p = facts.problem;
        if (!facts.hasSolution())
            return "<html><span style='color:#777'>No solution yet.</span></html>";
        return switch (type) {
            case AMBULANCE -> {
                Route r = facts.solution.route(id);
                yield "<html>%d trips · route cost <b>%.2f</b> (%.1f%% of the total)</html>".formatted(r.tripCount(),
                        facts.routeCost[id], facts.cost > 0 ? 100 * facts.routeCost[id] / facts.cost : 0);
            }
            case PATIENT -> {
                if (facts.visitsOf(id) == 0)
                    yield "<html><font color='#be1e1e'>Not transported.</font> Drag it onto an ambulance or a hospital "
                            + "with the route tool [R], or press G.</html>";
                Route r = facts.solution.route(facts.carrierOf(id));
                int t = facts.tripOf(id);
                int from = facts.tripStart(r, t);
                String twice = facts.visitsOf(id) > 1 ? "<br><font color='#be1e1e'>transported %d times</font>".formatted(facts.visitsOf(id)) : "";
                yield ("<html>Trip %d of ambulance %d: from %s %d (%.1f away) to hospital %d (%.1f further)<br>"
                        + "trip cost <b>%.2f</b> = %.1f × (%.1f + %.1f)%s</html>").formatted(t + 1, r.ambulance(),
                        p.isAmbulance(from) ? "its start" : "hospital", from, p.distance(from, id), r.hospital(t),
                        p.distance(id, r.hospital(t)), facts.tripCost(r, t), p.urgencyWeight(id),
                        p.distance(from, id), p.distance(id, r.hospital(t)), twice);
            }
            case HOSPITAL -> {
                int load = facts.load(id), beds = p.capacity(id);
                yield "<html>%d of %d beds taken · %s</html>".formatted(load, beds,
                        load > beds ? "<font color='#be1e1e'>%d over capacity</font>".formatted(load - beds) : (beds - load) + " free");
            }
        };
    }

    // HTML that wraps at the width of the side panel
    private static String wrap(String html) {
        return "<html><div style='width:240px'>" + html.replace("<html>", "").replace("</html>", "") + "</div></html>";
    }

    private void moved() {
        if (!updating && shown >= 0)
            editor.moveParticipant(shown, ((Number) x.getValue()).doubleValue(), ((Number) y.getValue()).doubleValue(),
                    List.of("move", shown));
    }

    private void row(int line, String label, JComponent control) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = line;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        form.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        form.add(control, c);
    }

    private static JSpinner coordinate() {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(0.0, -1e9, 1e9, 1.0));
        spinner.setEditor(new JSpinner.NumberEditor(spinner, "0.###"));
        return spinner;
    }

    private static JButton button(String text, String tooltip, Runnable body) {
        JButton b = new JButton(text);
        b.setToolTipText(tooltip);
        b.setMargin(new Insets(2, 6, 2, 6));
        b.addActionListener(e -> body.run());
        return b;
    }
}
