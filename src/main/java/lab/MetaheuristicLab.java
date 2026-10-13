package lab;

import com.formdev.flatlaf.FlatLightLaf;
import lab.model.Catalog;
import lab.ui.LabFrame;

import javax.swing.*;

/**
 * Desktop tool to run several metaheuristics side by side and watch them converge.
 * Run with: ./mvnw compile exec:java
 * An optional argument preselects a problem by name, e.g. -Dexec.args=WFLOP
 */
public final class MetaheuristicLab {

    private MetaheuristicLab() {
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> {
            LabFrame frame = new LabFrame(Catalog.problems());
            if (args.length > 0 && !frame.selectProblem(String.join(" ", args)))
                System.err.println("No problem matches '" + String.join(" ", args) + "'");
            frame.setVisible(true);
        });
    }
}
