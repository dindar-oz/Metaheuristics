package lab.problemlab.ui;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import lab.model.ParamValues;
import lab.problemlab.ProblemWorkbench;
import lab.problemlab.model.ComponentSpec;
import lab.problemlab.model.Evaluation;
import lab.problemlab.model.FileFormat;
import lab.problemlab.model.LabState;
import lab.problemlab.model.MutationStep;
import lab.problemlab.model.MutationStep.Acceptance;
import lab.problemlab.model.Workspace;
import representation.base.Representation;
import util.random.MersenneTwisterRNG;
import util.random.RNG;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/**
 * The Problem Lab window for one problem, generated from its {@link ProblemWorkbench}: create an instance (generator
 * or file), create a solution, apply mutation operators and edit both in the problem's own view, watching the cost
 * and the constraints change. Every action is recorded in the history, can be undone and redone, and the instance and
 * the solution can be saved at any time.
 * <p>
 * All randomness comes from one RNG seeded in the tool bar, so a sequence of actions can be replayed exactly.
 */
public final class ProblemLabFrame<S extends Representation<S>, P extends OptimizationProblem<S>> extends JFrame implements ViewHost {

    private static final int PLAY_MILLIS = 120;

    private final ProblemWorkbench<S, P> workbench;
    private final Workspace<S, P> workspace;
    private final ProblemView<S, P> view;

    private final ComponentPanel<Void, P> instancePanel;
    private final ComponentPanel<P, InitialSolutionGenerator<S>> solutionPanel;
    private final ComponentPanel<P, MutationOperator<S>> mutationPanel;
    private final JSpinner repetitions = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
    private final JComboBox<Acceptance> acceptance = new JComboBox<>(Acceptance.values());
    private final JToggleButton play = new JToggleButton("▶ Play");
    private final Timer playTimer = new Timer(PLAY_MILLIS, e -> playStep());
    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(Long.valueOf(42), null, null, Long.valueOf(1)));

    private final EvaluationPanel evaluationPanel = new EvaluationPanel();
    private final HistoryPanel historyPanel;
    private final JLabel status = new JLabel(" ");

    private final Action openInstance = action("Open instance…", "Read an instance from a file", KeyEvent.VK_O, 0, this::openInstance);
    private final Action saveInstance = action("Save instance…", "Write the current instance to a file", KeyEvent.VK_S, 0, this::saveInstance);
    private final Action openSolution = action("Open solution…", "Read a solution of the current instance from a file",
            KeyEvent.VK_O, KeyEvent.SHIFT_DOWN_MASK, this::openSolution);
    private final Action saveSolution = action("Save solution…", "Write the current solution to a file",
            KeyEvent.VK_S, KeyEvent.SHIFT_DOWN_MASK, this::saveSolution);
    private final Action undo = action("Undo", "Undo the last action", KeyEvent.VK_Z, 0, () -> workspace().undo());
    private final Action redo = action("Redo", "Redo the action undone last", KeyEvent.VK_Y, 0, () -> workspace().redo());
    private final Action clearSolution = action("Clear solution", "Remove the current solution, keeping the instance", -1, 0,
            () -> workspace().setSolution(null, "Cleared the solution"));

    private RNG rng = new MersenneTwisterRNG(42);
    private File directory;
    private Object playKey;          // merge key of the history entry the running play updates
    private int playTried, playAccepted;
    private boolean playing;         // true while a play step changes the workspace

    public ProblemLabFrame(ProblemWorkbench<S, P> workbench) {
        super("Problem Lab – " + workbench.name());
        this.workbench = workbench;
        this.workspace = new Workspace<>(workbench::evaluate);
        this.historyPanel = new HistoryPanel(workspace);
        this.view = workbench.createView(workspace, this);
        this.instancePanel = new ComponentPanel<>("Instance", workbench.instanceGenerators(), this::error);
        this.solutionPanel = new ComponentPanel<>("Solution", workbench.solutionGenerators(), this::error);
        this.mutationPanel = new ComponentPanel<>("Mutation", workbench.mutations(), this::error);

        setJMenuBar(buildMenuBar());
        setContentPane(buildContent());
        workspace.addListener(this::stateChanged);
        seed.addChangeListener(e -> {
            rng = new MersenneTwisterRNG((Long) seed.getValue());
            status("Random number generator reseeded with " + seed.getValue());
        });

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                playTimer.stop();
            }
        });
        stateChanged(null, null);
        setSize(1450, 920);
        setLocationRelativeTo(null);
    }

    public Workspace<S, P> workspace() {
        return workspace;
    }

    // ------------------------------------------------------------------ layout

    private JComponent buildContent() {
        JPanel root = new JPanel(new BorderLayout());
        root.add(buildToolBar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.add(evaluationPanel, BorderLayout.NORTH);
        center.add(view.component(), BorderLayout.CENTER);
        JSplitPane right = new JSplitPane(JSplitPane.VERTICAL_SPLIT, center, historyPanel);
        right.setResizeWeight(0.8);
        right.setDividerLocation(640);

        JScrollPane side = new JScrollPane(buildSidePanel(), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        side.setBorder(BorderFactory.createEmptyBorder());
        side.getVerticalScrollBar().setUnitIncrement(16);
        JSplitPane main = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, side, right);
        main.setDividerLocation(340);
        root.add(main, BorderLayout.CENTER);

        status.setBorder(new EmptyBorder(4, 8, 4, 8));
        root.add(status, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildToolBar() {
        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(new EmptyBorder(4, 6, 4, 6));
        for (Action a : List.of(openInstance, saveInstance, openSolution, saveSolution))
            bar.add(a);
        bar.addSeparator();
        bar.add(undo);
        bar.add(redo);
        bar.add(Box.createHorizontalGlue());
        bar.add(new JLabel("Seed "));
        seed.setEditor(new JSpinner.NumberEditor(seed, "#"));
        seed.setMaximumSize(new Dimension(150, 28));
        seed.setToolTipText("Seed of the random number generator used by the generators and operators; "
                + "setting it again replays the same random choices");
        bar.add(seed);
        return bar;
    }

    private JComponent buildSidePanel() {
        JButton generateInstance = new JButton("Generate instance");
        generateInstance.addActionListener(e -> generateInstance());
        instancePanel.addButton(generateInstance);
        instancePanel.addButton(new JButton(openInstance));

        JButton generateSolution = new JButton("Generate solution");
        generateSolution.addActionListener(e -> generateSolution());
        solutionPanel.addButton(generateSolution);
        solutionPanel.addButton(new JButton(openSolution));

        repetitions.setToolTipText("How many times the operator is applied, each time to the solution kept so far");
        acceptance.setToolTipText("Which neighbors are kept: always (a random walk) or only improvements (a hill climber); "
                + "feasible solutions count as better than infeasible ones");
        mutationPanel.addRow("Times", repetitions);
        mutationPanel.addRow("Keep neighbor", acceptance);
        JButton apply = new JButton("Apply");
        apply.setToolTipText("Apply the operator to the current solution");
        apply.addActionListener(e -> applyMutation());
        play.setToolTipText("Apply the operator again and again (Times per step) to watch the solution evolve");
        play.addActionListener(e -> setPlaying(play.isSelected()));
        mutationPanel.addButton(apply);
        mutationPanel.addButton(play);

        JPanel panel = new ComponentPanel.WidthTrackingPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(6, 4, 6, 4));
        for (JComponent c : List.of(instancePanel, solutionPanel, mutationPanel)) {
            c.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(c);
            panel.add(Box.createVerticalStrut(6));
        }
        if (!workbench.description().isEmpty()) {
            JTextArea about = ComponentPanel.wrapped();
            about.setText(workbench.description());
            about.setBorder(new EmptyBorder(4, 6, 4, 6));
            about.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(about);
        }
        return panel;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("File");
        file.add(openInstance);
        file.add(saveInstance);
        file.addSeparator();
        file.add(openSolution);
        file.add(saveSolution);
        file.addSeparator();
        file.add(action("Close", "Close this window", KeyEvent.VK_W, 0, this::dispose));
        bar.add(file);

        JMenu edit = new JMenu("Edit");
        edit.add(undo);
        edit.add(redo);
        edit.addSeparator();
        edit.add(clearSolution);
        bar.add(edit);

        List<Action> problemActions = view.actions();
        if (!problemActions.isEmpty()) {
            JMenu problem = new JMenu("Problem");
            problemActions.forEach(problem::add);
            bar.add(problem);
        }

        JMenu help = new JMenu("Help");
        help.add(action("Mouse and keyboard…", "How to edit the instance and the solution", KeyEvent.VK_F1, -1, this::showHelp));
        bar.add(help);
        return bar;
    }

    // ------------------------------------------------------------------ actions

    private void generateInstance() {
        ComponentSpec<Void, P> spec = instancePanel.selected();
        if (spec == null)
            return;
        ParamValues values = instancePanel.values();
        busy(() -> workspace.newInstance(spec.create(null, values), "Generated instance: " + spec.name() + " (" + values + ")"));
    }

    private void generateSolution() {
        ComponentSpec<P, InitialSolutionGenerator<S>> spec = solutionPanel.selected();
        if (spec == null || !requireInstance())
            return;
        ParamValues values = solutionPanel.values();
        busy(() -> {
            P problem = workspace.problem();
            S solution = spec.create(problem, values).generate(problem, rng);
            workspace.setSolution(solution, "Generated solution: " + spec.name() + (spec.params().isEmpty() ? "" : " (" + values + ")"));
        });
    }

    private void applyMutation() {
        setPlaying(false);
        ComponentSpec<P, MutationOperator<S>> spec = mutationPanel.selected();
        if (spec == null || !requireSolution())
            return;
        busy(() -> {
            int times = (Integer) repetitions.getValue();
            Acceptance rule = (Acceptance) acceptance.getSelectedItem();
            MutationStep<S> step = run(spec, times, rule);
            String action = times == 1 ? spec.name() + (step.changed() == 0 ? " (no change)" : "")
                    : "%s × %d, keep %s: %d changed, %d kept".formatted(spec.name(), times, rule, step.changed(), step.accepted());
            workspace.change(workspace.problem(), step.solution(), action);
        });
    }

    private MutationStep<S> run(ComponentSpec<P, MutationOperator<S>> spec, int times, Acceptance rule) {
        P problem = workspace.problem();
        return MutationStep.run(problem, workspace.solution(), spec.create(problem, mutationPanel.values()), times, rule, rng);
    }

    private void setPlaying(boolean on) {
        if (on && (mutationPanel.selected() == null || !requireSolution()))
            on = false;
        play.setSelected(on);
        play.setText(on ? "■ Stop" : "▶ Play");
        if (on) {
            playKey = new Object();
            playTried = playAccepted = 0;
            playTimer.start();
        } else
            playTimer.stop();
    }

    private void playStep() {
        ComponentSpec<P, MutationOperator<S>> spec = mutationPanel.selected();
        Acceptance rule = (Acceptance) acceptance.getSelectedItem();
        try {
            MutationStep<S> step = run(spec, (Integer) repetitions.getValue(), rule);
            playTried += step.tried();
            playAccepted += step.accepted();
            playing = true;
            workspace.change(workspace.problem(), step.solution(),
                    "%s played, keep %s: %d applied, %d kept".formatted(spec.name(), rule, playTried, playAccepted), playKey);
        } catch (RuntimeException e) {
            setPlaying(false);
            error(e.getMessage());
        } finally {
            playing = false;
        }
    }

    private void openInstance() {
        workbench.instanceFormat().ifPresentOrElse(format -> chooseFile(format, false, file -> {
            P problem = format.read(null, file.toPath());
            workspace.newInstance(problem, "Opened instance " + file.getName());
        }), () -> error(workbench.name() + " instances cannot be read from files"));
    }

    private void saveInstance() {
        if (!requireInstance())
            return;
        workbench.instanceFormat().ifPresentOrElse(format -> chooseFile(format, true, file -> {
            format.write(null, workspace.problem(), file.toPath());
            status("Saved the instance to " + file);
        }), () -> error(workbench.name() + " instances cannot be saved"));
    }

    private void openSolution() {
        if (!requireInstance())
            return;
        workbench.solutionFormat().ifPresentOrElse(format -> chooseFile(format, false, file -> {
            S solution = format.read(workspace.problem(), file.toPath());
            workspace.setSolution(solution, "Opened solution " + file.getName());
        }), () -> error(workbench.name() + " solutions cannot be read from files"));
    }

    private void saveSolution() {
        if (!requireSolution())
            return;
        workbench.solutionFormat().ifPresentOrElse(format -> chooseFile(format, true, file -> {
            format.write(workspace.problem(), workspace.solution(), file.toPath());
            status("Saved the solution to " + file + " (it refers to participants by ID: save the instance too if you edited it)");
        }), () -> error(workbench.name() + " solutions cannot be saved"));
    }

    @FunctionalInterface
    private interface FileAction {
        void run(File file) throws IOException;
    }

    private void chooseFile(FileFormat<?, ?> format, boolean save, FileAction action) {
        JFileChooser chooser = new JFileChooser(directory);
        chooser.setFileFilter(new FileNameExtensionFilter(format.description() + " (*." + format.extension() + ")", format.extension()));
        int choice = save ? chooser.showSaveDialog(this) : chooser.showOpenDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION)
            return;
        File file = chooser.getSelectedFile();
        directory = file.getParentFile();
        if (save && !file.getName().contains("."))
            file = new File(file.getPath() + "." + format.extension());
        if (save && file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + " exists. Replace it?", "Save",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;
        File chosen = file;
        busy(() -> {
            try {
                action.run(chosen);
            } catch (IOException e) {
                throw new IllegalArgumentException("Cannot access " + chosen + ": " + e.getMessage(), e);
            }
        });
    }

    private void showHelp() {
        String text = view.help();
        JEditorPane pane = new JEditorPane("text/html", text == null ? "<html>This view has no interactive editing.</html>" : text);
        pane.setEditable(false);
        pane.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.setPreferredSize(new Dimension(620, 520));
        JOptionPane.showMessageDialog(this, scroll, "Problem Lab – mouse and keyboard", JOptionPane.PLAIN_MESSAGE);
    }

    // ------------------------------------------------------------------ state

    private void stateChanged(LabState<S, P> previous, LabState<S, P> current) {
        if (!playing && playTimer.isRunning())
            setPlaying(false);       // another action interrupts the play
        boolean instance = current != null, solution = instance && current.hasSolution();
        int index = workspace.currentIndex();
        Evaluation before = index > 0 ? workspace.history().get(index - 1).evaluation() : null;
        evaluationPanel.show(solution ? current.evaluation() : null, before);
        historyPanel.refresh();
        view.stateChanged(previous, current);

        saveInstance.setEnabled(instance && workbench.instanceFormat().isPresent());
        openInstance.setEnabled(workbench.instanceFormat().isPresent());
        openSolution.setEnabled(instance && workbench.solutionFormat().isPresent());
        saveSolution.setEnabled(solution && workbench.solutionFormat().isPresent());
        clearSolution.setEnabled(solution);
        undo.setEnabled(workspace.canUndo());
        redo.setEnabled(workspace.canRedo());
        solutionPanel.setEnabled(instance);
        mutationPanel.setEnabled(solution);
        setTitle("Problem Lab – " + workbench.name() + (instance ? " – " + workbench.describe(current.problem()) : ""));
        if (instance && !playing)
            status(current.action());
    }

    // ------------------------------------------------------------------ ViewHost

    @Override
    public void status(String message) {
        status.setText(message == null || message.isEmpty() ? " " : message);
    }

    @Override
    public void error(String message) {
        JOptionPane.showMessageDialog(this, message, "Problem Lab", JOptionPane.WARNING_MESSAGE);
    }

    @Override
    public RNG rng() {
        return rng;
    }

    @Override
    public Window window() {
        return this;
    }

    // ------------------------------------------------------------------ helpers

    private boolean requireInstance() {
        if (!workspace.hasInstance())
            error("Generate or open an instance first.");
        return workspace.hasInstance();
    }

    private boolean requireSolution() {
        if (!requireInstance())
            return false;
        if (workspace.solution() == null)
            error("Generate or open a solution first.");
        return workspace.solution() != null;
    }

    // runs an action with a wait cursor; invalid input (IllegalArgumentException) is reported, not thrown
    private void busy(Runnable action) {
        Cursor cursor = getCursor();
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            action.run();
        } catch (IllegalArgumentException | IllegalStateException | IndexOutOfBoundsException e) {
            error(e.getMessage());
        } finally {
            setCursor(cursor);
        }
    }

    // key < 0: no accelerator; modifiers < 0: the key alone, otherwise with the menu shortcut key (Ctrl / Cmd)
    private static Action action(String name, String tooltip, int key, int modifiers, Runnable body) {
        Action a = new AbstractAction(name) {
            @Override
            public void actionPerformed(ActionEvent e) {
                body.run();
            }
        };
        a.putValue(Action.SHORT_DESCRIPTION, tooltip);
        if (key >= 0)
            a.putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(key,
                    modifiers < 0 ? 0 : Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx() | modifiers));
        return a;
    }
}
