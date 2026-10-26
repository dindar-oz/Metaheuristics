package problems.pecto.aco.gui;

import com.formdev.flatlaf.FlatLightLaf;
import problems.pecto.PECTOProblem;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * PECTOACO diagnosis: watch a PECTOACO run on an instance, live or step by step, with its components shown as they
 * work: the ants' construction (ratings, shortlists, roulette draws, provisional and final hospitals), the candidate
 * lists, the pheromone and its update, the improvement of the iteration best, and the run's convergence. Steps go
 * forward and back at the level of iterations, ants, construction steps or single decisions.
 * <p>
 * The run is PECTOACO's own (see DiagnosticColony and its test): for the same settings and seed, the diagnosis shows
 * exactly the run that PECTOACO makes.
 * <p>
 * {@code java problems.pecto.aco.gui.PECTOACODiagnosis}
 */
public final class PECTOACODiagnosis extends JFrame {

    private final SetupPanel setup = new SetupPanel(this::start);
    private final MapView map = new MapView();
    private final DecisionPanel decision = new DecisionPanel();
    private final ConstructionPanel construction = new ConstructionPanel();
    private final IterationPanel iteration = new IterationPanel();
    private final PheromonePanel pheromone = new PheromonePanel();
    private final NodePanel node = new NodePanel();
    private final HistoryCharts charts = new HistoryCharts();
    private final JTabbedPane tabs = new JTabbedPane();

    private final JButton restart = new JButton("⏮ Restart");
    private final JButton back = new JButton("◀ Back");
    private final JButton play = new JButton("▶ Play");
    private final JButton step = new JButton("Step ▶");
    private final JButton nextIteration = new JButton("Next iteration ⏭");
    private final JComboBox<EventType.Level> level = new JComboBox<>(EventType.Level.values());
    private final JSlider delay = new JSlider(0, 1000, 0);
    private final JLabel delayLabel = new JLabel();
    private final JSpinner toIteration = new JSpinner(new SpinnerNumberModel(10, 0, 1_000_000, 1));
    private final JButton go = new JButton("Go");
    private final JLabel position = new JLabel(" ");
    private final JLabel status = new JLabel(" ");

    private Session session;
    private Frame shown;
    private RunController.Status shownStatus;

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> new PECTOACODiagnosis().setVisible(true));
    }

    public PECTOACODiagnosis() {
        super("PECTOACO diagnosis");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(new Dimension(1500, 950));
        setLocationByPlatform(true);

        tabs.addTab("Decision", decision);
        tabs.addTab("Ant", construction);
        tabs.addTab("Iteration", iteration);
        tabs.addTab("Pheromone", pheromone);
        tabs.addTab("Node", node);
        tabs.setToolTipTextAt(0, "The decision of the current event and every number behind it");
        tabs.setToolTipTextAt(1, "The ant being built: ratings, positions, trips, beds");
        tabs.setToolTipTextAt(2, "The iteration's ants, the improvement of the best, the pheromone update");
        tabs.setToolTipTextAt(3, "The pheromone as a whole: distributions and heat map");
        tabs.setToolTipTextAt(4, "The selected participant: candidates, pheromone, role");
        tabs.addChangeListener(e -> render(true));

        JPanel mapPanel = new JPanel(new BorderLayout());
        mapPanel.add(layers(), BorderLayout.NORTH);
        mapPanel.add(map, BorderLayout.CENTER);
        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, mapPanel, tabs);
        center.setResizeWeight(0.55);
        center.setDividerLocation(640);
        charts.setPreferredSize(new Dimension(800, 230));
        JSplitPane main = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, charts);
        main.setResizeWeight(0.78);
        JSplitPane all = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, setup, main);
        all.setDividerLocation(300);

        JPanel root = new JPanel(new BorderLayout());
        root.add(controls(), BorderLayout.NORTH);
        root.add(all, BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
        keys(root);

        Timer timer = new Timer(50, e -> render(false));
        timer.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                timer.stop();
                if (session != null)
                    session.controller.dispose();
            }
        });
        SwingUtilities.invokeLater(() -> {
            try {
                start(setup.problem(), setup.settings(), setup.seed());
            } catch (java.io.IOException | IllegalArgumentException ex) {
                status.setText(ex.getMessage());
            }
        });
    }

    // ---- building

    private JComponent controls() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        restart.setToolTipText("Back to the start of the run (Home)");
        back.setToolTipText("Back to the previous event of the level (Left arrow); replays from the nearest checkpoint");
        play.setToolTipText("Play: stop at every event of the level for the delay, or run as fast as possible with delay 0 (Space)");
        step.setToolTipText("On to the next event of the level (Right arrow)");
        nextIteration.setToolTipText("Run to the end of the next iteration (Page Down)");
        restart.addActionListener(e -> withRun(c -> c.restart()));
        back.addActionListener(e -> withRun(RunController::back));
        play.addActionListener(e -> withRun(c -> {
            if (c.playing() && c.status() == RunController.Status.RUNNING)
                c.pause();
            else
                c.play();
        }));
        step.addActionListener(e -> withRun(RunController::step));
        nextIteration.addActionListener(e -> withRun(c -> c.runToIteration(lastDone() + 1)));
        go.addActionListener(e -> withRun(c -> c.runToIteration((Integer) toIteration.getValue())));
        level.setSelectedItem(EventType.Level.DECISION);
        level.addActionListener(e -> withRun(c -> c.setLevel((EventType.Level) level.getSelectedItem())));
        level.setToolTipText("What Step, Back and Play stop at: each iteration, each ant (and the improvement and pheromone "
                + "update), each construction step, or each decision");
        delay.addChangeListener(e -> {
            delayLabel.setText(delay.getValue() == 0 ? "fastest" : delay.getValue() + " ms");
            withRun(c -> c.setDelay(delay.getValue()));
        });
        delay.setPreferredSize(new Dimension(130, delay.getPreferredSize().height));
        delay.setToolTipText("Play: pause at each stop for this long (0: as fast as possible, showing a frame every 40 ms)");
        delayLabel.setText("fastest");
        delayLabel.setPreferredSize(new Dimension(50, 20));

        bar.add(restart);
        bar.add(back);
        bar.add(play);
        bar.add(step);
        bar.addSeparator();
        bar.add(new JLabel(" Level "));
        bar.add(level);
        bar.add(new JLabel("  Delay "));
        bar.add(delay);
        bar.add(delayLabel);
        bar.addSeparator();
        bar.add(nextIteration);
        bar.add(new JLabel("  to iteration "));
        toIteration.setPreferredSize(new Dimension(80, toIteration.getPreferredSize().height));
        toIteration.setMaximumSize(toIteration.getPreferredSize());
        bar.add(toIteration);
        bar.add(go);
        bar.addSeparator();
        bar.add(position);
        return bar;
    }

    private JComponent layers() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        p.add(toggle("Best so far", map.showBest, v -> map.showBest = v, "The best solution so far (faint while an ant is built)"));
        p.add(toggle("Ant's routes", map.showConstruction, v -> map.showConstruction = v, "The ant being built: trips solid, provisional deliveries dashed"));
        p.add(toggle("Pheromone", map.showPheromone, v -> map.showPheromone = v, "Patient pheromone τ(prev, q): stronger is thicker and darker"));
        p.add(toggle("Candidates", map.showCandidates, v -> map.showCandidates = v, "The candidate patients of the selected node, with τ/τmax"));
        JCheckBox labels = toggle("Labels", map.showLabels, v -> map.showLabels = v, "Participant IDs");
        p.add(labels);
        JButton fit = new JButton("Fit");
        fit.addActionListener(e -> map.fit());
        p.add(fit);
        map.addPropertyChangeListener("labels", e -> labels.setSelected(map.showLabels));
        return p;
    }

    private JCheckBox toggle(String text, boolean on, java.util.function.Consumer<Boolean> set, String tip) {
        JCheckBox box = new JCheckBox(text, on);
        box.setToolTipText(tip);
        box.addActionListener(e -> {
            set.accept(box.isSelected());
            map.repaint();
        });
        return box;
    }

    private void keys(JComponent root) {
        bind(root, KeyEvent.VK_RIGHT, "step", () -> step.doClick());
        bind(root, KeyEvent.VK_LEFT, "back", () -> back.doClick());
        bind(root, KeyEvent.VK_SPACE, "play", () -> play.doClick());
        bind(root, KeyEvent.VK_HOME, "restart", () -> restart.doClick());
        bind(root, KeyEvent.VK_PAGE_DOWN, "next iteration", () -> nextIteration.doClick());
    }

    private static void bind(JComponent c, int key, String name, Runnable action) {
        c.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        c.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    // ---- the run

    private void start(PECTOProblem problem, ColonySettings settings, long seed) {
        if (session != null)
            session.controller.dispose();
        RunController controller = new RunController(problem, settings, seed);
        controller.setLevel((EventType.Level) level.getSelectedItem());
        controller.setDelay(delay.getValue());
        session = new Session(controller);
        session.onSelection(() -> {
            map.repaint();
            render(true);
        });
        map.session(session);
        map.firePropertyChange("labels", !map.showLabels, map.showLabels);
        decision.session(session);
        construction.session(session);
        iteration.session(session);
        pheromone.session(session);
        node.session(session);
        charts.clear();
        shown = null;
        setTitle("PECTOACO diagnosis · " + problem + " · seed " + seed);
        controller.start();
    }

    private void withRun(java.util.function.Consumer<RunController> command) {
        if (session != null)
            command.accept(session.controller);
    }

    private int lastDone() {
        if (shown == null)
            return -1;
        return shown.type() == EventType.ITERATION_DONE ? shown.iteration() : shown.iteration() - 1;
    }

    // shows the latest frame; again = also when it was shown already (a tab or the selection changed)
    private void render(boolean again) {
        if (session == null)
            return;
        RunController c = session.controller;
        Frame f = c.latest();
        RunController.Status st = c.status();
        boolean newFrame = f != null && f != shown;
        if (newFrame || st != shownStatus || again)
            controlsFor(st, c);
        if (f == null || (!newFrame && !again))
            return;
        shown = f;
        boolean paused = st == RunController.Status.PAUSED;
        map.show(f);
        charts.show(f);
        switch (tabs.getSelectedIndex()) {
            case 0 -> decision.show(f);
            case 1 -> construction.show(f, paused);
            case 2 -> iteration.show(f);
            case 3 -> pheromone.show(f);
            default -> node.show(f);
        }
        String where = "<html><b>" + f.type().label + "</b> · iteration " + f.iteration()
                + (f.ant() >= 0 ? " · ant " + f.ant() + (f.explorer() ? " (explorer)" : "") : "")
                + (f.construction() != null ? " · step " + f.step() + "/" + session.problem.patientCount() : "")
                + " · best " + Style.num(f.bestCost()) + " · event #" + String.format("%,d", f.seq()) + "</html>";
        position.setText(where);
    }

    private void controlsFor(RunController.Status st, RunController c) {
        shownStatus = st;
        boolean paused = st == RunController.Status.PAUSED, running = st == RunController.Status.RUNNING;
        boolean idle = paused || st == RunController.Status.FINISHED;
        step.setEnabled(paused);
        back.setEnabled(idle && shown != null && shown.seq() > 1);
        play.setEnabled(paused || running);
        play.setText(running && c.playing() ? "⏸ Pause" : "▶ Play");
        nextIteration.setEnabled(paused || running);
        go.setEnabled(idle || running);
        String text = switch (st) {
            case STARTING -> "Starting…";
            case RUNNING -> c.playing() ? "Playing" : "Running…";
            case PAUSED -> "Paused";
            case BUSY -> "Replaying from a checkpoint…";
            case FINISHED -> "The run ended";
            case FAILED -> "Failed: " + c.failure();
        };
        String note = session.practicality.note();
        status.setText("<html>" + text + " · " + c.checkpointInfo() + (note.isEmpty() ? "" : " · <i>" + note + "</i>")
                + "</html>");
        if (st == RunController.Status.FAILED)
            JOptionPane.showMessageDialog(this, "The run failed: " + c.failure(), "PECTOACO diagnosis", JOptionPane.ERROR_MESSAGE);
    }
}
