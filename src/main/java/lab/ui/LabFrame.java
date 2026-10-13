package lab.ui;

import lab.model.ParamValues;
import lab.model.LabProblem;
import metaheuristic.termination.CostBasedTC;
import metaheuristic.termination.EvaluationBasedTC;
import metaheuristic.termination.TerminalCondition;
import metaheuristic.termination.TimeBasedTC;
import util.random.Seeds;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The lab window: choose a problem, configure any number of algorithm runs, run them in parallel and watch them
 * converge. Runs execute on a thread pool; the UI reads their data with a Swing timer and never blocks them.
 */
public final class LabFrame extends JFrame {

    private static final int REFRESH_MILLIS = 150;

    private final List<LabProblem> problems;

    // problem
    private final JComboBox<LabProblem> problemChoice;
    private final ParamTable problemTable = new ParamTable(this::error);
    private ParamValues problemValues;

    // algorithms
    private final JComboBox<String> algorithmChoice = new JComboBox<>();
    private final DefaultListModel<RunConfig> configs = new DefaultListModel<>();
    private final JList<RunConfig> configList = new JList<>(configs);
    private final JTextField configName = new JTextField();
    private final ParamTable configTable = new ParamTable(this::error);

    // run settings
    private final JSpinner evaluationBudget = new JSpinner(longModel(100_000L, 1L, 10_000L));
    private final JSpinner timeLimit = new JSpinner(new SpinnerNumberModel(0.0, 0.0, 1e7, 1.0));
    private final JTextField targetCost = new JTextField(6);
    private final JSpinner seed = new JSpinner(longModel(42L, Long.MIN_VALUE, 1L));
    private final JButton runButton = new JButton("▶  Run");
    private final JButton stopButton = new JButton("■  Stop");
    private final JButton exportButton = new JButton("Export CSV");

    // results
    private final ConvergenceView view = new ConvergenceView();
    private final ResultsTableModel results = new ResultsTableModel();
    private final JTable resultsTable = new JTable(results);
    private final JButton viewButton = new JButton("View solution");
    private final JLabel status = new JLabel("Ready");

    private final Timer timer = new Timer(REFRESH_MILLIS, e -> refresh());
    private List<RunTracker> runs = new ArrayList<>();
    private ExecutorService pool;
    private long startNanos;

    public LabFrame(List<LabProblem> problems) {
        super("Metaheuristic Lab");
        this.problems = problems;
        this.problemChoice = new JComboBox<>(problems.toArray(new LabProblem[0]));

        setContentPane(buildContent());
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                stopRuns();
                timer.stop();
            }
        });

        problemChoice.addActionListener(e -> selectProblem());
        configList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting())
                selectConfig();
        });
        configName.addActionListener(e -> renameConfig());
        configName.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                renameConfig();
            }
        });
        runButton.addActionListener(e -> startRuns());
        stopButton.addActionListener(e -> stopRuns());
        exportButton.addActionListener(e -> exportCsv());
        selectProblem();
        setRunning(false);

        setSize(1400, 850);
        setLocationRelativeTo(null);
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildToolBar(), BorderLayout.NORTH);

        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, view, buildResultsTable());
        right.setResizeWeight(0.7);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, buildSidePanel(), right);
        main.setDividerLocation(370);
        root.add(main, BorderLayout.CENTER);

        status.setBorder(new EmptyBorder(4, 8, 4, 8));
        root.add(status, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(new EmptyBorder(4, 6, 4, 6));
        runButton.setToolTipText("Run all configured algorithms in parallel");
        stopButton.setToolTipText("Cancel all running algorithms");
        bar.add(runButton);
        bar.add(stopButton);
        bar.addSeparator();
        bar.add(labeled("Max evaluations", evaluationBudget, 110));
        bar.add(labeled("Time limit (s, 0 = none)", timeLimit, 70));
        targetCost.setToolTipText("Stop a run when its best cost reaches this value (empty = no target)");
        bar.add(labeled("Target cost", targetCost, 70));
        seed.setToolTipText("Base seed: run i uses a seed derived from it, so repeating a run gives identical results");
        bar.add(labeled("Seed", seed, 160));
        bar.add(Box.createHorizontalGlue());
        bar.add(exportButton);
        return bar;
    }

    // Long-valued spinner model (the primitive-argument constructors would silently use double)
    private static SpinnerNumberModel longModel(long value, long min, long step) {
        return new SpinnerNumberModel(Long.valueOf(value), Long.valueOf(min), Long.valueOf(Long.MAX_VALUE), Long.valueOf(step));
    }

    private static JComponent labeled(String label, JComponent field, int width) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        p.setOpaque(false);
        p.add(new JLabel(label));
        field.setPreferredSize(new Dimension(width, field.getPreferredSize().height));
        p.add(field);
        p.setMaximumSize(p.getPreferredSize()); // keep toolbar items compact
        return p;
    }

    private JComponent buildSidePanel() {
        JPanel problemPanel = new JPanel(new BorderLayout(4, 4));
        problemPanel.setBorder(titled("Problem"));
        problemPanel.add(problemChoice, BorderLayout.NORTH);
        problemPanel.add(scroll(problemTable, 90), BorderLayout.CENTER);

        JButton add = new JButton("Add");
        add.setToolTipText("Add a run of the selected algorithm");
        add.addActionListener(e -> addConfig((String) algorithmChoice.getSelectedItem()));
        JButton duplicate = new JButton("Duplicate");
        duplicate.addActionListener(e -> duplicateConfig());
        JButton remove = new JButton("Remove");
        remove.addActionListener(e -> removeConfig());

        JPanel addRow = new JPanel(new BorderLayout(4, 0));
        addRow.add(algorithmChoice, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);
        JPanel listButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        listButtons.add(duplicate);
        listButtons.add(remove);

        configList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        configList.setVisibleRowCount(6);
        JPanel listPanel = new JPanel(new BorderLayout(4, 4));
        listPanel.add(addRow, BorderLayout.NORTH);
        listPanel.add(new JScrollPane(configList), BorderLayout.CENTER);
        listPanel.add(listButtons, BorderLayout.SOUTH);

        JPanel namePanel = new JPanel(new BorderLayout(4, 0));
        namePanel.add(new JLabel("Name"), BorderLayout.WEST);
        namePanel.add(configName, BorderLayout.CENTER);
        JPanel editPanel = new JPanel(new BorderLayout(4, 4));
        editPanel.add(namePanel, BorderLayout.NORTH);
        editPanel.add(scroll(configTable, 160), BorderLayout.CENTER);

        JPanel algorithmsPanel = new JPanel(new BorderLayout(4, 8));
        algorithmsPanel.setBorder(titled("Algorithm runs"));
        algorithmsPanel.add(listPanel, BorderLayout.NORTH);
        algorithmsPanel.add(editPanel, BorderLayout.CENTER);

        JPanel side = new JPanel(new BorderLayout(0, 8));
        side.setBorder(new EmptyBorder(6, 6, 6, 6));
        side.add(problemPanel, BorderLayout.NORTH);
        side.add(algorithmsPanel, BorderLayout.CENTER);
        return side;
    }

    private JComponent buildResultsTable() {
        JTable table = resultsTable;
        table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateViewButton());
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2)
                    viewSolution();
            }
        });
        table.setRowHeight(22);
        int[] widths = {40, 120, 130, 90, 90, 80, 70, 110, 170, 400};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(2).setMinWidth(115);
        table.setDefaultRenderer(Double.class, new NumberRenderer());
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        viewButton.setToolTipText("Show the best solution of the selected run (or double-click a row)");
        viewButton.addActionListener(e -> viewSolution());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        actions.add(viewButton);
        actions.add(new JLabel("Select a run to view its best solution"));

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(titled("Results"));
        panel.add(actions, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }

    // Best cost with 7 significant digits (objectives can be tiny, e.g. 0.001460), times with 3 decimals
    private static final class NumberRenderer extends javax.swing.table.DefaultTableCellRenderer {
        NumberRenderer() {
            setHorizontalAlignment(RIGHT);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focus, row, column);
            if (value instanceof Double)
                setText(table.convertColumnIndexToModel(column) == 3
                        ? String.format("%.7g", (Double) value)
                        : String.format("%.3f", (Double) value));
            return this;
        }
    }

    private static JScrollPane scroll(JComponent c, int height) {
        JScrollPane s = new JScrollPane(c);
        s.setPreferredSize(new Dimension(300, height));
        return s;
    }

    private static TitledBorder titled(String title) {
        return BorderFactory.createTitledBorder(title);
    }

    // Selects the first problem whose name contains the given text (case-insensitive); returns false if none does
    public boolean selectProblem(String nameFragment) {
        for (int i = 0; i < problemChoice.getItemCount(); i++)
            if (problemChoice.getItemAt(i).name().toLowerCase().contains(nameFragment.toLowerCase())) {
                problemChoice.setSelectedIndex(i);
                return true;
            }
        return false;
    }

    // ------------------------------------------------------------------ configuration

    private LabProblem problem() {
        return (LabProblem) problemChoice.getSelectedItem();
    }

    // A new problem type: default parameters and one run of every compatible algorithm
    private void selectProblem() {
        LabProblem p = problem();
        problemValues = ParamValues.defaults(p.params());
        problemTable.edit(p.params(), problemValues);
        algorithmChoice.setModel(new DefaultComboBoxModel<>(p.algorithmNames().toArray(new String[0])));
        configs.clear();
        for (String a : p.algorithmNames())
            addConfig(a);
        configList.setSelectedIndex(0);
    }

    private void addConfig(String algorithm) {
        if (algorithm == null)
            return;
        RunConfig c = new RunConfig(algorithm, uniqueLabel(algorithm), ParamValues.defaults(problem().algorithmParams(algorithm)));
        configs.addElement(c);
        configList.setSelectedValue(c, true);
    }

    private void duplicateConfig() {
        RunConfig selected = configList.getSelectedValue();
        if (selected == null)
            return;
        RunConfig c = new RunConfig(selected.algorithm, uniqueLabel(selected.algorithm), selected.values.copy());
        configs.addElement(c);
        configList.setSelectedValue(c, true);
    }

    private void removeConfig() {
        int i = configList.getSelectedIndex();
        if (i < 0)
            return;
        configs.remove(i);
        if (!configs.isEmpty())
            configList.setSelectedIndex(Math.min(i, configs.size() - 1));
    }

    private void selectConfig() {
        RunConfig c = configList.getSelectedValue();
        if (c == null) {
            configTable.clear();
            configName.setText("");
            return;
        }
        configName.setText(c.label);
        configTable.edit(problem().algorithmParams(c.algorithm), c.values);
    }

    private void renameConfig() {
        RunConfig c = configList.getSelectedValue();
        String name = configName.getText().trim();
        if (c != null && !name.isEmpty() && !name.equals(c.label)) {
            c.label = name;
            configList.repaint();
        }
    }

    private String uniqueLabel(String algorithm) {
        int n = 1;
        for (int i = 0; i < configs.size(); i++)
            if (configs.get(i).algorithm.equals(algorithm))
                n++;
        return n == 1 ? algorithm : algorithm + " (" + n + ")";
    }

    // ------------------------------------------------------------------ running

    private void startRuns() {
        stopEditing();
        if (configs.isEmpty()) {
            error("Add at least one algorithm run.");
            return;
        }
        TerminalCondition stop;
        try {
            stop = stopCondition();
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }

        LabProblem.Session session;
        try {
            session = problem().newSession(problemValues.copy());
        } catch (RuntimeException e) {
            error("Cannot create the problem: " + e.getMessage());
            return;
        }

        long baseSeed = ((Number) seed.getValue()).longValue();
        long budget = ((Number) evaluationBudget.getValue()).longValue();
        pool = Executors.newFixedThreadPool(Math.min(configs.size(), Runtime.getRuntime().availableProcessors()), r -> {
            Thread t = new Thread(r, "lab-run");
            t.setDaemon(true);
            return t;
        });
        runs = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (int i = 0; i < configs.size(); i++) {
            RunConfig c = configs.get(i);
            ParamValues values = c.values.copy();
            RunTracker tracker = new RunTracker(i + 1, c.label, c.algorithm, values.toString(), Seeds.derive(baseSeed, i), budget);
            try {
                tracker.setRun(session.start(c.algorithm, values, tracker.seed, stop, tracker, pool));
            } catch (IllegalArgumentException e) {
                tracker.setStartError(e.getMessage());
                invalid.add(c.label + ": " + e.getMessage());
            }
            runs.add(tracker);
        }
        pool.shutdown(); // runs keep going; the pool ends when they are done

        results.setRuns(runs);
        view.setRuns(runs);
        updateViewButton();
        startNanos = System.nanoTime();
        setRunning(true);
        status.setText("Running " + runs.size() + " runs on " + session.describe());
        timer.start();
        if (!invalid.isEmpty())
            error("Some runs could not start:\n" + String.join("\n", invalid));
    }

    private TerminalCondition stopCondition() {
        List<TerminalCondition> conditions = new ArrayList<>();
        conditions.add(new EvaluationBasedTC(((Number) evaluationBudget.getValue()).longValue()));
        double seconds = ((Number) timeLimit.getValue()).doubleValue();
        if (seconds > 0)
            conditions.add(new TimeBasedTC((long) (seconds * 1000)));
        String target = targetCost.getText().trim();
        if (!target.isEmpty()) {
            try {
                conditions.add(new CostBasedTC(Double.parseDouble(target)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Target cost must be a number or empty.");
            }
        }
        return TerminalCondition.anyOf(conditions.toArray(new TerminalCondition[0]));
    }

    private void stopRuns() {
        for (RunTracker t : runs)
            t.cancel();
    }

    // Timer callback on the Swing thread
    private void refresh() {
        boolean allDone = true;
        for (RunTracker t : runs) {
            t.drain();
            allDone &= t.isDone();
        }
        view.refresh();
        int selected = resultsTable.getSelectedRow();
        results.refresh();
        if (selected >= 0 && selected < resultsTable.getRowCount())
            resultsTable.setRowSelectionInterval(selected, selected);
        updateViewButton();
        if (allDone) {
            timer.stop();
            setRunning(false);
            status.setText(String.format("Finished %d runs in %.1f s", runs.size(), (System.nanoTime() - startNanos) / 1e9));
        }
    }

    private void setRunning(boolean running) {
        runButton.setEnabled(!running);
        stopButton.setEnabled(running);
        exportButton.setEnabled(!running && !runs.isEmpty());
        problemChoice.setEnabled(!running);
    }

    private void stopEditing() {
        for (JTable t : new JTable[]{problemTable, configTable})
            if (t.isEditing())
                t.getCellEditor().stopCellEditing();
        renameConfig();
    }

    // ------------------------------------------------------------------ solutions

    private RunTracker selectedRun() {
        int row = resultsTable.getSelectedRow();
        return row < 0 ? null : runs.get(resultsTable.convertRowIndexToModel(row));
    }

    private void updateViewButton() {
        RunTracker t = selectedRun();
        viewButton.setEnabled(t != null && t.hasSolution());
    }

    private void viewSolution() {
        RunTracker t = selectedRun();
        if (t == null || !t.hasSolution())
            return;
        JComponent view;
        try {
            view = t.viewBestSolution();
        } catch (RuntimeException e) {
            error("Cannot show the solution: " + e.getMessage());
            return;
        }
        String title = String.format("#%d %s - %s solution", t.index, t.label, t.isDone() ? "best" : "best so far");
        JDialog dialog = new JDialog(this, title, false);
        dialog.setContentPane(view);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    // ------------------------------------------------------------------ export

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("convergence.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        Path file = chooser.getSelectedFile().toPath();
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
            out.println("run,name,algorithm,seed,evaluations,seconds,bestCost,parameters");
            for (RunTracker t : runs)
                for (int i = 0; i < t.costs.size(); i++)
                    out.printf("%d,\"%s\",%s,%d,%.0f,%.3f,%s,\"%s\"%n", t.index, t.label, t.algorithm, t.seed,
                            t.evaluations.get(i), t.seconds.get(i), t.costs.get(i), t.parameters);
            status.setText("Exported to " + file);
        } catch (IOException e) {
            error("Export failed: " + e.getMessage());
        }
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Metaheuristic Lab", JOptionPane.WARNING_MESSAGE);
    }
}
