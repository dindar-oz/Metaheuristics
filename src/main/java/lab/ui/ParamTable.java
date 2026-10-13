package lab.ui;

import lab.model.ParamSpec;
import lab.model.ParamValues;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellEditor;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Two-column (parameter, value) editor for a ParamValues object. Invalid input is rejected with a message.
 */
public final class ParamTable extends JTable {

    private final Model model;

    public ParamTable(Consumer<String> errorHandler) {
        this.model = new Model(errorHandler);
        setModel(model);
        setRowHeight(24);
        setFillsViewportHeight(true);
        getTableHeader().setReorderingAllowed(false);
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    }

    public void edit(List<ParamSpec> specs, ParamValues values) {
        if (isEditing())
            getCellEditor().stopCellEditing();
        model.specs = specs;
        model.values = values;
        model.fireTableDataChanged();
    }

    public void clear() {
        edit(Collections.<ParamSpec>emptyList(), null);
    }

    @Override
    public TableCellEditor getCellEditor(int row, int column) {
        ParamSpec spec = model.specs.get(row);
        if (column == 1 && spec.kind() == ParamSpec.Kind.CHOICE)
            return new DefaultCellEditor(new JComboBox<>(spec.choices().toArray(new String[0])));
        return super.getCellEditor(row, column);
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        int row = rowAtPoint(e.getPoint());
        return row < 0 ? null : model.specs.get(row).description();
    }

    private static final class Model extends AbstractTableModel {
        private final Consumer<String> errorHandler;
        private List<ParamSpec> specs = Collections.emptyList();
        private ParamValues values;

        Model(Consumer<String> errorHandler) {
            this.errorHandler = errorHandler;
        }

        @Override
        public int getRowCount() {
            return specs.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Parameter" : "Value";
        }

        @Override
        public Object getValueAt(int row, int column) {
            ParamSpec spec = specs.get(row);
            return column == 0 ? spec.name() : String.valueOf(values.get(spec.name()));
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 1;
        }

        @Override
        public void setValueAt(Object value, int row, int column) {
            ParamSpec spec = specs.get(row);
            try {
                values.set(spec.name(), spec.parse(String.valueOf(value)));
            } catch (IllegalArgumentException e) {
                errorHandler.accept(e.getMessage());
            }
            fireTableRowsUpdated(row, row);
        }
    }
}
