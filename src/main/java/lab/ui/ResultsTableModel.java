package lab.ui;

import metaheuristic.run.ProgressSnapshot;
import metaheuristic.run.RunResult;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Live status of every run: refreshed from the runs' progress snapshots by the lab's timer.
 */
final class ResultsTableModel extends AbstractTableModel {

    private static final String[] COLUMNS = {"#", "Run", "Status", "Best cost", "Evaluations", "Iterations",
            "Time (s)", "Best found at (s)", "Seed", "Parameters"};

    private List<RunTracker> runs = new ArrayList<>();

    void setRuns(List<RunTracker> runs) {
        this.runs = runs;
        fireTableDataChanged();
    }

    void refresh() {
        if (!runs.isEmpty())
            fireTableRowsUpdated(0, runs.size() - 1);
    }

    @Override
    public int getRowCount() {
        return runs.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
            case 4:
            case 5:
            case 8:
                return Long.class;
            case 3:
            case 6:
            case 7:
                return Double.class;
            default:
                return String.class;
        }
    }

    @Override
    public Object getValueAt(int row, int column) {
        RunTracker t = runs.get(row);
        RunResult<?> result = t.result();
        ProgressSnapshot p = result != null ? result.finalProgress() : t.latestProgress();
        switch (column) {
            case 0:
                return (long) t.index;
            case 1:
                return t.label;
            case 2:
                return t.status();
            case 8:
                return t.seed;
            case 9:
                return t.parameters;
            default:
                break;
        }
        if (p == null)
            return null;
        switch (column) {
            case 3:
                return p.bestCost();
            case 4:
                return p.evaluations();
            case 5:
                return p.iterations();
            case 6:
                return p.elapsedMillis() / 1000.0;
            default:
                return p.timeToBestMillis() / 1000.0;
        }
    }
}
