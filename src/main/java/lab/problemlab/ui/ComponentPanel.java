package lab.problemlab.ui;

import lab.model.ParamValues;
import lab.problemlab.model.ComponentSpec;
import lab.ui.ParamTable;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * One section of the side panel: a choice of components (instance generators, solution generators or mutation
 * operators), the description and parameters of the chosen one, and the section's buttons. Every component keeps
 * its own parameter values, so switching back and forth does not lose edits.
 */
final class ComponentPanel<I, T> extends JPanel {

    private final JComboBox<ComponentSpec<I, T>> choice;
    private final JTextArea description = wrapped();
    private final ParamTable table;
    private final JScrollPane tableScroll;
    private final JPanel rows = new JPanel(new GridBagLayout());
    private final JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final Map<ComponentSpec<I, T>, ParamValues> values = new IdentityHashMap<>();

    @SuppressWarnings("unchecked")
    ComponentPanel(String title, List<ComponentSpec<I, T>> specs, Consumer<String> errors) {
        super(new BorderLayout(0, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Color.LIGHT_GRAY), title,
                        TitledBorder.LEADING, TitledBorder.TOP, getFont().deriveFont(Font.BOLD)),
                BorderFactory.createEmptyBorder(2, 6, 8, 6)));
        choice = new JComboBox<>(specs.toArray(new ComponentSpec[0]));
        table = new ParamTable(errors);
        tableScroll = new JScrollPane(table);

        JPanel top = new JPanel(new BorderLayout(0, 3));
        top.add(choice, BorderLayout.NORTH);
        top.add(description, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);
        add(tableScroll, BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 4));
        bottom.add(rows, BorderLayout.NORTH);
        bottom.add(buttons, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        choice.addActionListener(e -> showSelected());
        if (specs.isEmpty())
            choice.setEnabled(false);
        showSelected();
    }

    // the chosen component, null if there are none
    @SuppressWarnings("unchecked")
    ComponentSpec<I, T> selected() {
        return (ComponentSpec<I, T>) choice.getSelectedItem();
    }

    // the parameter values of the chosen component, with any edit in progress committed
    ParamValues values() {
        if (table.isEditing())
            table.getCellEditor().stopCellEditing();
        return values.get(selected());
    }

    void addButton(AbstractButton button) {
        buttons.add(button);
    }

    // a labeled control below the parameters
    void addRow(String label, JComponent control) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = rows.getComponentCount() / 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 6);
        rows.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        rows.add(control, c);
    }

    private void showSelected() {
        ComponentSpec<I, T> spec = selected();
        if (spec == null) {
            description.setText("none available");
            tableScroll.setVisible(false);
            return;
        }
        description.setText(spec.description());
        ParamValues v = values.computeIfAbsent(spec, s -> ParamValues.defaults(s.params()));
        table.edit(spec.params(), v);
        tableScroll.setVisible(!spec.params().isEmpty());
        int height = table.getTableHeader().getPreferredSize().height + spec.params().size() * table.getRowHeight() + 4;
        tableScroll.setPreferredSize(new Dimension(200, height));
        revalidate();
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (Component c : buttons.getComponents())
            c.setEnabled(enabled);
    }

    // a read-only text that wraps at the width of its container
    static JTextArea wrapped() {
        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setFocusable(false);
        text.setOpaque(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setForeground(Color.DARK_GRAY);
        text.setFont(UIManager.getFont("Label.font").deriveFont(11.5f));
        text.setBorder(BorderFactory.createEmptyBorder());
        return text;
    }

    // a panel for a scroll pane that is as wide as the scroll pane, so that wrapped texts wrap at its width
    static final class WidthTrackingPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
