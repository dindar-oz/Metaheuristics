package experimentrunner.gui;

import com.formdev.flatlaf.FlatLightLaf;
import experimentrunner.Cli;
import experimentrunner.Control;
import experimentrunner.ExperimentSpec;
import experimentrunner.ExperimentStatus;
import experimentrunner.QueueState;
import experimentrunner.Workspace;
import experimentrunner.WorkerInfo;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * The window of the experiment runner: the queue with the progress of every experiment, the worker and its settings,
 * and the journal, summary and runs in flight of the selected experiment. It only reads and changes the workspace's
 * files (through {@link Control}); the experiments run in the worker, so closing the window stops nothing.
 * {@code java experimentrunner.gui.ExperimentRunnerApp [workspace]} or {@code runner.cmd gui}.
 */
public final class ExperimentRunnerApp extends JFrame {

    private static final long REFRESH_MILLIS = 2000;

    private final Control control;
    private final Workspace ws;
    private final ExperimentsModel experiments = new ExperimentsModel();
    private final RunningModel running = new RunningModel();
    private final JTable table = new JTable(experiments);
    private final JLabel workerLabel = new JLabel();
    private final JButton startWorker = new JButton("Start worker");
    private final JButton stopWorker = new JButton("Stop after runs");
    private final JButton stopNow = new JButton("Stop now");
    private final JSpinner threads = new JSpinner(new SpinnerNumberModel(1, 1, 512, 1));
    private final JToggleButton pauseAll = new JToggleButton("Pause all");
    private final JButton pause = new JButton("Pause"), pauseNow = new JButton("Pause now"), resume = new JButton("Resume"),
            cancel = new JButton("Cancel"), up = new JButton("Up"), down = new JButton("Down"),
            summarize = new JButton("Summarize"), openFolder = new JButton("Open folder");
    private final JTextArea journal = text(), summary = text();
    private boolean refreshing;
    private String shownId;
    private long shownJournalTime, shownSummaryTime;

    public static void main(String[] args) {
        open(args.length > 0 ? Workspace.open(Path.of(args[0])) : Workspace.openDefault());
    }

    /** Opens the workspace's window, or brings it to the front if it is open already (in any process). */
    public static void open(Workspace ws) {
        SingleWindow single;
        try {
            single = SingleWindow.claim(ws.root());
        } catch (IOException e) {
            single = null;      // cannot tell: open a window anyway
        }
        if (single == null && SingleWindow.focusOpen(ws.root()))
            return;
        SingleWindow owner = single;
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            ExperimentRunnerApp app = new ExperimentRunnerApp(ws);
            if (owner != null) {
                owner.onFocus(() -> SwingUtilities.invokeLater(app::bringToFront));
                app.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent e) {
                        owner.close();
                    }
                });
            }
            app.setVisible(true);
        });
    }

    // restores the window if minimized and raises it; Windows lets only the foreground process raise its windows,
    // and a moment of always-on-top gets past that
    private void bringToFront() {
        if ((getExtendedState() & ICONIFIED) != 0)
            setExtendedState(getExtendedState() & ~ICONIFIED);
        setVisible(true);
        setAlwaysOnTop(true);
        toFront();
        requestFocus();
        setAlwaysOnTop(false);
    }

    private ExperimentRunnerApp(Workspace ws) {
        super("Experiment Runner — " + ws.root());
        this.ws = ws;
        this.control = new Control(ws);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(new Dimension(1150, 760));
        setLocationByPlatform(true);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        top.add(workerLabel);
        top.add(startWorker);
        top.add(stopWorker);
        top.add(stopNow);
        top.add(Box.createHorizontalStrut(16));
        top.add(new JLabel("Threads"));
        top.add(threads);
        top.add(pauseAll);

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(4).setCellRenderer(new ProgressRenderer());
        int[] widths = {30, 230, 300, 90, 160, 80, 60, 70};
        for (int i = 0; i < widths.length; i++)
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !refreshing)
                showSelected(true);
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton submit = new JButton("Submit spec…");
        for (JButton b : List.of(submit, pause, pauseNow, resume, cancel, up, down, summarize, openFolder))
            actions.add(b);
        JPanel queue = new JPanel(new BorderLayout());
        queue.add(new JScrollPane(table), BorderLayout.CENTER);
        queue.add(actions, BorderLayout.SOUTH);

        JTabbedPane details = new JTabbedPane();
        details.addTab("Journal", new JScrollPane(journal));
        details.addTab("Summary", new JScrollPane(summary));
        JTable runningTable = new JTable(running);
        runningTable.setRowHeight(22);
        details.addTab("Runs in flight", new JScrollPane(runningTable));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, queue, details);
        split.setResizeWeight(0.45);
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);

        submit.addActionListener(e -> submit());
        pause.addActionListener(e -> onSelected(id -> control.pause(id, false)));
        pauseNow.addActionListener(e -> onSelected(id -> control.pause(id, true)));
        resume.addActionListener(e -> onSelected(control::resume));
        cancel.addActionListener(e -> onSelected(id -> {
            if (JOptionPane.showConfirmDialog(this, "Cancel " + id + "? Its runs in flight stop; finished runs are kept.",
                    "Cancel experiment", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                control.cancel(id);
        }));
        up.addActionListener(e -> onSelected(id -> control.move(id, -1)));
        down.addActionListener(e -> onSelected(id -> control.move(id, 1)));
        summarize.addActionListener(e -> onSelected(id -> {
            control.summarize(id);
            shownSummaryTime = 0;
        }));
        openFolder.addActionListener(e -> onSelected(id -> {
            try {
                Desktop.getDesktop().open(ws.dir(id).toFile());
            } catch (IOException ex) {
                throw new IllegalStateException("cannot open " + ws.dir(id) + ": " + ex.getMessage());
            }
        }));
        startWorker.addActionListener(e -> startWorker());
        stopWorker.addActionListener(e -> act(() -> control.stopWorker(false)));
        stopNow.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(this, "Stop the worker now? Its runs in flight are dropped and run again "
                    + "when it starts next.", "Stop now", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                act(() -> control.stopWorker(true));
        });
        threads.addChangeListener(e -> {
            if (!refreshing)
                act(() -> control.threads((Integer) threads.getValue()));
        });
        pauseAll.addActionListener(e -> act(() -> control.pauseAll(pauseAll.isSelected())));

        refresh();
        new Timer((int) REFRESH_MILLIS, e -> refresh()).start();
    }

    private static JTextArea text() {
        JTextArea t = new JTextArea();
        t.setEditable(false);
        t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        t.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        return t;
    }

    // ---- actions

    private void act(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Experiment runner", JOptionPane.WARNING_MESSAGE);
        }
        refresh();
    }

    private void onSelected(Consumer<String> action) {
        String id = selectedId();
        if (id == null) {
            JOptionPane.showMessageDialog(this, "Select an experiment first.");
            return;
        }
        act(() -> action.accept(id));
    }

    private void submit() {
        JFileChooser chooser = new JFileChooser(ws.root().toFile());
        chooser.setFileFilter(new FileNameExtensionFilter("Experiment spec (*.json)", "json"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
            return;
        act(() -> {
            ExperimentSpec s = control.submit(chooser.getSelectedFile().toPath());
            JOptionPane.showMessageDialog(this, "Submitted " + s.id + ": " + s.runs().size() + " runs."
                    + (control.workerRunning() ? "" : "\nThe worker is not running; start it to run the experiment."));
        });
    }

    private void startWorker() {
        startWorker.setEnabled(false);
        workerLabel.setText("Starting the worker…");
        new SwingWorker<Long, Void>() {
            @Override
            protected Long doInBackground() {
                return control.startWorker(null);
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    JOptionPane.showMessageDialog(ExperimentRunnerApp.this, cause.getMessage(), "Start worker",
                            JOptionPane.WARNING_MESSAGE);
                }
                refresh();
            }
        }.execute();
    }

    // ---- refresh from the files

    private void refresh() {
        refreshing = true;
        try {
            String selected = selectedId();
            QueueState q = ws.queue();
            List<Row> rows = new ArrayList<>();
            int i = 1;
            for (QueueState.Entry e : q.experiments) {
                Row r = new Row();
                r.index = i++;
                r.entry = e;
                r.status = ws.status(e.id);
                try {
                    r.title = ws.spec(e.id).title;
                } catch (RuntimeException ex) {
                    r.title = "?";
                }
                rows.add(r);
            }
            experiments.set(rows);
            for (int k = 0; k < rows.size(); k++)
                if (rows.get(k).entry.id.equals(selected))
                    table.setRowSelectionInterval(k, k);

            boolean alive = ws.workerAlive();
            WorkerInfo w = ws.worker();
            long now = System.currentTimeMillis();
            if (alive && w != null && w.fresh(now)) {
                workerLabel.setText(String.format("● Worker %s · pid %d · %d runs in flight", w.state, w.pid, w.running.size()));
                workerLabel.setForeground(new Color(0x1a7f37));
            } else {
                boolean waiting = q.experiments.stream().anyMatch(e -> e.state == QueueState.State.QUEUED);
                workerLabel.setText(alive ? "● Worker starting…" : waiting ? "○ Worker not running: queued experiments wait"
                        : "○ Worker not running");
                workerLabel.setForeground(alive ? new Color(0x9a6700) : waiting ? new Color(0xcf222e) : Color.GRAY);
            }
            startWorker.setEnabled(!alive);
            stopWorker.setEnabled(alive);
            stopNow.setEnabled(alive);
            threads.setValue(q.threads);
            pauseAll.setSelected(q.paused);
            pauseAll.setText(q.paused ? "Resume all" : "Pause all");
            running.set(alive && w != null ? w.running : List.of());
            showSelected(false);
        } catch (RuntimeException ex) {
            workerLabel.setText("Cannot read the workspace: " + ex.getMessage());
        } finally {
            refreshing = false;
        }
    }

    private String selectedId() {
        int row = table.getSelectedRow();
        return row < 0 ? null : experiments.rows.get(table.convertRowIndexToModel(row)).entry.id;
    }

    // the journal and summary of the selected experiment, read again when the files changed
    private void showSelected(boolean force) {
        String id = selectedId();
        QueueState.State state = null;
        for (Row r : experiments.rows)
            if (r.entry.id.equals(id))
                state = r.entry.state;
        pause.setEnabled(state == QueueState.State.QUEUED || state == QueueState.State.PAUSED);
        pauseNow.setEnabled(pause.isEnabled());
        resume.setEnabled(state != null && state != QueueState.State.QUEUED && state != QueueState.State.DONE);
        cancel.setEnabled(state != null && state != QueueState.State.DONE && state != QueueState.State.CANCELLED);
        for (JButton b : List.of(up, down, summarize, openFolder))
            b.setEnabled(id != null);
        if (id == null) {
            journal.setText("Select an experiment to see its journal.");
            summary.setText("");
            shownId = null;
            return;
        }
        if (force || !id.equals(shownId)) {
            shownId = id;
            shownJournalTime = shownSummaryTime = 0;
        }
        shownJournalTime = load(ws.journalFile(id), journal, shownJournalTime, "No journal.");
        shownSummaryTime = load(ws.summaryFile(id), summary, shownSummaryTime,
                "No summary yet. It is written when the experiment is done, or now with Summarize.");
    }

    private static long load(Path file, JTextArea area, long shown, String missing) {
        try {
            if (!Files.exists(file)) {
                area.setText(missing);
                return 0;
            }
            long modified = Files.getLastModifiedTime(file).toMillis();
            if (modified != shown) {
                area.setText(Files.readString(file, StandardCharsets.UTF_8));
                area.setCaretPosition(0);
            }
            return modified;
        } catch (IOException e) {
            return shown;
        }
    }

    // ---- table models

    private static final class Row {
        int index;
        QueueState.Entry entry;
        ExperimentStatus status;
        String title;
    }

    private static final class ExperimentsModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"#", "Experiment", "Title", "State", "Progress", "Runs", "Running", "Left"};
        List<Row> rows = new ArrayList<>();

        void set(List<Row> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            Row row = rows.get(r);
            ExperimentStatus s = row.status;
            return switch (c) {
                case 0 -> row.index;
                case 1 -> row.entry.id;
                case 2 -> row.title;
                case 3 -> row.entry.state + (row.entry.state == QueueState.State.PAUSED && row.entry.interrupt ? " (now)" : "")
                        + (row.entry.message == null ? "" : ": " + row.entry.message);
                case 4 -> s == null || s.total == 0 ? 0.0 : (double) s.done / s.total;
                case 5 -> s == null ? "" : s.done + "/" + s.total;
                case 6 -> s == null ? "" : Integer.toString(s.running);
                case 7 -> s == null || row.entry.state == QueueState.State.DONE ? "" : Cli.durationText(s.etaMillis);
                default -> "";
            };
        }
    }

    private static final class RunningModel extends AbstractTableModel {
        private static final String[] COLUMNS = {"Experiment", "Run (config, instance, repeat)", "Seed", "Iterations",
                "Evaluations", "Best cost", "Running for"};
        List<WorkerInfo.Running> rows = new ArrayList<>();

        void set(List<WorkerInfo.Running> rows) {
            this.rows = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int c) {
            return COLUMNS[c];
        }

        @Override
        public Object getValueAt(int r, int c) {
            WorkerInfo.Running x = rows.get(r);
            return switch (c) {
                case 0 -> x.experiment;
                case 1 -> x.run;
                case 2 -> x.seed;
                case 3 -> String.format(Locale.ROOT, "%,d", x.iterations);
                case 4 -> String.format(Locale.ROOT, "%,d", x.evaluations);
                case 5 -> x.iterations == 0 ? "" : String.format(Locale.ROOT, "%.4f", x.bestCost);
                case 6 -> Cli.durationText(System.currentTimeMillis() - x.started);
                default -> "";
            };
        }
    }

    private static final class ProgressRenderer extends JProgressBar implements TableCellRenderer {
        ProgressRenderer() {
            super(0, 1000);
            setStringPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected, boolean focus, int row, int col) {
            double v = value instanceof Double d ? d : 0;
            setValue((int) Math.round(v * 1000));
            setString(String.format(Locale.ROOT, "%.0f%%", v * 100));
            return this;
        }
    }
}
