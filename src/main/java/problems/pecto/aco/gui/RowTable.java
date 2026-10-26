package problems.pecto.aco.gui;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A read-only table of rows of strings, with an optional participant ID per row (clicking the row selects it) and
 * an optional highlight per row.
 */
final class RowTable extends JTable {

    private final String[] columns;
    private List<Object[]> rows = new ArrayList<>();
    private List<Integer> ids = new ArrayList<>();
    private List<Boolean> highlighted = new ArrayList<>();
    private boolean updating;

    RowTable(String... columns) {
        this.columns = columns;
        setModel(new AbstractTableModel() {
            @Override
            public int getRowCount() {
                return rows.size();
            }

            @Override
            public int getColumnCount() {
                return columns.length;
            }

            @Override
            public String getColumnName(int c) {
                return columns[c];
            }

            @Override
            public Object getValueAt(int r, int c) {
                return rows.get(r)[c];
            }
        });
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setRowHeight(22);
        getTableHeader().setReorderingAllowed(false);
        setFillsViewportHeight(true);
        DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean selected, boolean focus, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, selected, focus, r, c);
                if (!selected)
                    comp.setBackground(r < highlighted.size() && highlighted.get(r) ? new Color(255, 236, 179) : t.getBackground());
                setHorizontalAlignment(c == 0 ? LEFT : v instanceof String s && !s.isEmpty() && (Character.isDigit(s.charAt(0))
                        || s.charAt(0) == '-' || s.charAt(0) == '–') ? RIGHT : LEFT);
                return comp;
            }
        };
        setDefaultRenderer(Object.class, renderer);
    }

    // a row; id: the participant it is about (-1: none)
    static Object[] row(Object... cells) {
        return cells;
    }

    void setRows(List<Object[]> rows, List<Integer> ids, List<Boolean> highlighted) {
        updating = true;
        int selected = getSelectedRow();
        this.rows = rows;
        this.ids = ids;
        this.highlighted = highlighted;
        ((AbstractTableModel) getModel()).fireTableDataChanged();
        if (selected >= 0 && selected < rows.size())
            getSelectionModel().setSelectionInterval(selected, selected);
        updating = false;
    }

    // calls the listener with the participant of the row the user selects
    void onSelect(IntConsumer listener) {
        getSelectionModel().addListSelectionListener(e -> {
            int r = getSelectedRow();
            if (!e.getValueIsAdjusting() && !updating && r >= 0 && r < ids.size() && ids.get(r) != null && ids.get(r) >= 0)
                listener.accept(ids.get(r));
        });
    }

    int selectedRow() {
        return getSelectedRow();
    }

    // preferred column widths, in pixels
    RowTable widths(int... widths) {
        for (int c = 0; c < widths.length && c < getColumnCount(); c++)
            getColumnModel().getColumn(c).setPreferredWidth(widths[c]);
        return this;
    }

    String[] columns() {
        return columns;
    }
}
