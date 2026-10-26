package problems.pecto.aco.gui;

import lab.model.ParamSpec;
import lab.model.ParamValues;
import lab.ui.ParamTable;
import problems.pecto.PECTOFiles;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.nio.file.Path;
import java.util.List;

/**
 * The run to diagnose: the instance (the lab's PECTO parameters, or a .pecto file), PECTOACO's parameters (the lab's,
 * by the same names) and the seed. Start builds the run.
 */
final class SetupPanel extends JPanel {

    interface StartListener {
        void start(PECTOProblem problem, ColonySettings settings, long seed);
    }

    private final List<ParamSpec> instanceSpecs = ColonySettings.problem().params();
    private final ParamValues instanceValues = ParamValues.defaults(instanceSpecs);
    private final List<ParamSpec> algorithmSpecs = ColonySettings.params();
    private final ParamValues algorithmValues = ParamValues.defaults(algorithmSpecs);
    private final JSpinner seed = new JSpinner(new SpinnerNumberModel(Long.valueOf(1), Long.valueOf(0), Long.valueOf(Long.MAX_VALUE), Long.valueOf(1)));
    private final JLabel fileLabel = new JLabel();
    private final JButton clearFile = new JButton("Use the parameters");
    private Path file;

    SetupPanel(StartListener listener) {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        setPreferredSize(new Dimension(300, 600));

        // a small instance, where every view is practical
        instanceValues.set("instance", "custom");
        instanceValues.set("ambulances (custom)", 5);
        instanceValues.set("patients (custom)", 30);
        instanceValues.set("hospitals (custom)", 3);
        instanceValues.set("beds per hospital (custom)", 12);
        algorithmValues.set("ants", 10);

        ParamTable instance = new ParamTable(this::error);
        instance.edit(instanceSpecs, instanceValues);
        ParamTable algorithm = new ParamTable(this::error);
        algorithm.edit(algorithmSpecs, algorithmValues);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(title("Instance"));
        top.add(scroll(instance, 165));
        JPanel fileRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton open = new JButton("Open .pecto file…");
        open.addActionListener(e -> openFile());
        clearFile.addActionListener(e -> {
            file = null;
            fileLabel.setText("");
            clearFile.setVisible(false);
        });
        clearFile.setVisible(false);
        fileRow.add(open);
        fileRow.add(clearFile);
        fileRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(fileRow);
        fileLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        top.add(fileLabel);
        top.add(Box.createVerticalStrut(6));
        top.add(title("PECTOACO"));
        add(top, BorderLayout.NORTH);
        add(new JScrollPane(algorithm), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        bottom.add(new JLabel("Seed"));
        seed.setPreferredSize(new Dimension(110, seed.getPreferredSize().height));
        bottom.add(seed);
        JButton start = new JButton("Start");
        start.setToolTipText("Build the run with these settings; it stops at its first event");
        start.addActionListener(e -> {
            if (instance.isEditing())
                instance.getCellEditor().stopCellEditing();
            if (algorithm.isEditing())
                algorithm.getCellEditor().stopCellEditing();
            try {
                listener.start(problem(), ColonySettings.from(algorithmValues), ((Number) seed.getValue()).longValue());
            } catch (IllegalArgumentException | java.io.IOException ex) {
                error(ex.getMessage());
            }
        });
        bottom.add(start);
        add(bottom, BorderLayout.SOUTH);
    }

    // the instance of the current settings
    PECTOProblem problem() throws java.io.IOException {
        if (file != null)
            return PECTOFiles.readInstance(file);
        long instanceSeed = instanceValues.getInt("instance seed");
        String instance = instanceValues.getChoice("instance");
        if (instance.equals("custom"))
            return PECTOInstances.random(instanceValues.getInt("ambulances (custom)"), instanceValues.getInt("patients (custom)"),
                    instanceValues.getInt("hospitals (custom)"), instanceValues.getInt("beds per hospital (custom)"), instanceSeed);
        return PECTOInstances.paper(Integer.parseInt(instance.substring(instance.indexOf(':') + 1).trim()), instanceSeed);
    }

    ColonySettings settings() {
        return ColonySettings.from(algorithmValues);
    }

    long seed() {
        return ((Number) seed.getValue()).longValue();
    }

    private void openFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("PECTO instances (*.pecto)", PECTOFiles.INSTANCE_EXTENSION));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            file = chooser.getSelectedFile().toPath();
            fileLabel.setText("<html>Instance file: <b>" + file.getFileName() + "</b> (the parameters above are not used)</html>");
            clearFile.setVisible(true);
        }
    }

    private static JLabel title(String text) {
        JLabel l = new JLabel("<html><b>" + text + "</b></html>");
        l.setAlignmentX(Component.LEFT_ALIGNMENT);
        return l;
    }

    private static JScrollPane scroll(ParamTable table, int height) {
        JScrollPane s = new JScrollPane(table);
        s.setPreferredSize(new Dimension(280, height));
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        s.setAlignmentX(Component.LEFT_ALIGNMENT);
        return s;
    }

    private void error(String message) {
        JOptionPane.showMessageDialog(this, message, "PECTOACO diagnosis", JOptionPane.WARNING_MESSAGE);
    }
}
