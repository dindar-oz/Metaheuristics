package lab.view;

import problems.wflop.CellLayout;
import problems.wflop.DiscreteWFLOP;
import problems.wflop.LayoutEvaluation;
import problems.wflop.WFLOPProblem;
import problems.wflop.WindFarm;
import problems.wflop.WindFarmParameters;
import problems.wflop.WindScenario;
import representation.DoubleVector;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

/**
 * Draws a wind farm layout: the grid, the turbines colored by their power relative to an unwaked turbine and,
 * for a single wind direction, the wake cones and the wind direction. Hover over a turbine for its numbers.
 * Works for the paper's real-vector encodings and for the discrete (cell set) problem.
 */
public final class WFLOPVisualizer {

    private WFLOPVisualizer() {
    }

    // for WFLOPProblem (DE, PSO): the decoded layout
    public static SolutionVisualizer<DoubleVector, WFLOPProblem> continuous() {
        return (problem, solution) -> layoutView(problem.farm(), problem.decode(solution));
    }

    // for DiscreteWFLOP (EA, SA, local search)
    public static SolutionVisualizer<CellLayout, DiscreteWFLOP> discrete() {
        return (problem, layout) -> layoutView(problem.farm(), layout.cells());
    }

    public static JComponent layoutView(WindFarm farm, int[] cells) {
        return new LayoutPanel(farm, cells);
    }

    private static final class LayoutPanel extends JPanel {
        private final WindFarm farm;
        private final int[] cells;
        private final FarmCanvas canvas;
        private final JComboBox<String> windChoice = new JComboBox<>();
        private final JCheckBox showWakes = new JCheckBox("Show wakes", true);

        LayoutPanel(WindFarm farm, int[] cells) {
            super(new BorderLayout(0, 6));
            this.farm = farm;
            this.cells = cells;
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

            LayoutEvaluation e = farm.evaluate(cells);
            JLabel summary = new JLabel(String.format("<html><b>%d turbines</b> &nbsp; power %.2f kW &nbsp; cost %.4f &nbsp; "
                            + "objective %.7f<br>efficiency (Eq. 10) %.2f%% &nbsp; wake efficiency %.2f%% &nbsp; %s</html>",
                    e.turbines(), e.power(), e.cost(), e.objective(), 100 * e.efficiency(), 100 * e.wakeEfficiency(), farm.wind()));

            windChoice.addItem("All directions (expected power)");
            for (WindScenario.State s : farm.wind().states())
                windChoice.addItem(String.format("Wind from %.0f° at %.1f m/s", s.direction, s.speed));
            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            controls.add(new JLabel("Wind:"));
            controls.add(windChoice);
            controls.add(showWakes);

            JPanel top = new JPanel(new BorderLayout(0, 6));
            top.add(summary, BorderLayout.NORTH);
            top.add(controls, BorderLayout.SOUTH);

            canvas = new FarmCanvas();
            add(top, BorderLayout.NORTH);
            add(canvas, BorderLayout.CENTER);

            windChoice.addActionListener(ev -> update());
            showWakes.addActionListener(ev -> update());
            update();
        }

        private void update() {
            int state = windChoice.getSelectedIndex() - 1; // -1 = expected over all states
            showWakes.setEnabled(state >= 0);
            canvas.show(state, farm.turbinePowers(cells, state), farm.freeTurbinePower(state), state >= 0 && showWakes.isSelected());
        }

        private final class FarmCanvas extends JPanel {
            private static final int MARGIN = 36;
            private static final int LEGEND = 80;
            private int state = -1;
            private double[] powers = new double[0];
            private double freePower = 1;
            private boolean wakes;

            FarmCanvas() {
                setBackground(Color.WHITE);
                setPreferredSize(new Dimension(720, 640));
                setToolTipText("");
            }

            void show(int state, double[] powers, double freePower, boolean wakes) {
                this.state = state;
                this.powers = powers;
                this.freePower = freePower;
                this.wakes = wakes;
                repaint();
            }

            private double plotSize() {
                return Math.max(50, Math.min(getWidth() - 2 * MARGIN - LEGEND, getHeight() - 2 * MARGIN));
            }

            private double px(double x) {
                return MARGIN + x / farm.parameters().farmSize() * plotSize();
            }

            private double py(double y) {
                return MARGIN + (1 - y / farm.parameters().farmSize()) * plotSize();
            }

            @Override
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                WindFarmParameters p = farm.parameters();
                double size = plotSize();
                int n = farm.cellsPerSide();

                // grid with coordinates in meters
                g.setFont(g.getFont().deriveFont(10f));
                for (int k = 0; k <= n; k++) {
                    double t = MARGIN + size * k / n;
                    g.setColor(new Color(225, 225, 225));
                    g.draw(new java.awt.geom.Line2D.Double(MARGIN, t, MARGIN + size, t));
                    g.draw(new java.awt.geom.Line2D.Double(t, MARGIN, t, MARGIN + size));
                    g.setColor(Color.GRAY);
                    String label = String.valueOf((int) (p.cellSize() * k));
                    g.drawString(label, (float) t - 8, (float) (MARGIN + size + 14));
                    g.drawString(label, 2, (float) (MARGIN + size - size * k / n + 4));
                }
                g.setColor(Color.DARK_GRAY);
                g.draw(new java.awt.geom.Rectangle2D.Double(MARGIN, MARGIN, size, size));

                WindScenario.State wind = state >= 0 ? farm.wind().states().get(state) : null;
                if (wakes && wind != null)
                    drawWakes(g, wind, size);

                double lowest = 1;
                for (double pw : powers)
                    lowest = Math.min(lowest, pw / freePower);
                double scaleLow = Math.min(0.7, lowest);

                double radius = Math.max(5, Math.min(11, size / n / 5));
                for (int k = 0; k < cells.length; k++) {
                    double x = px(farm.centerX(cells[k])), y = py(farm.centerY(cells[k]));
                    Shape dot = new Ellipse2D.Double(x - radius, y - radius, 2 * radius, 2 * radius);
                    g.setColor(color((powers[k] / freePower - scaleLow) / (1 - scaleLow)));
                    g.fill(dot);
                    g.setColor(new Color(40, 40, 40));
                    g.draw(dot);
                }

                drawLegend(g, size, scaleLow);
                if (wind != null)
                    drawWindArrow(g, wind, size);
                g.dispose();
            }

            // A cone per turbine: radius r + alpha x along the flow, clipped to the farm
            private void drawWakes(Graphics2D g, WindScenario.State wind, double size) {
                WindFarmParameters p = farm.parameters();
                double length = p.farmSize() * 1.5;
                double r = p.rotorRadius(), rEnd = farm.wakeModel().wakeRadius(length);
                double fx = wind.flowX, fy = wind.flowY, nx = -fy, ny = fx;
                Shape oldClip = g.getClip();
                g.clip(new java.awt.geom.Rectangle2D.Double(MARGIN, MARGIN, size, size));
                g.setColor(new Color(70, 130, 180, 45));
                for (int cell : cells) {
                    double x = farm.centerX(cell), y = farm.centerY(cell);
                    Path2D cone = new Path2D.Double();
                    cone.moveTo(px(x + nx * r), py(y + ny * r));
                    cone.lineTo(px(x + fx * length + nx * rEnd), py(y + fy * length + ny * rEnd));
                    cone.lineTo(px(x + fx * length - nx * rEnd), py(y + fy * length - ny * rEnd));
                    cone.lineTo(px(x - nx * r), py(y - ny * r));
                    cone.closePath();
                    g.fill(cone);
                }
                g.setClip(oldClip);
            }

            private void drawWindArrow(Graphics2D g, WindScenario.State wind, double size) {
                double cx = MARGIN + size + LEGEND / 2.0, cy = MARGIN + size - 40, len = 26;
                double dx = wind.flowX * len, dy = -wind.flowY * len;   // screen y grows downwards
                g.setColor(new Color(30, 90, 160));
                g.setStroke(new BasicStroke(2.5f));
                g.draw(new java.awt.geom.Line2D.Double(cx - dx, cy - dy, cx + dx, cy + dy));
                double angle = Math.atan2(dy, dx);
                Path2D head = new Path2D.Double();
                head.moveTo(cx + dx, cy + dy);
                head.lineTo(cx + dx - 10 * Math.cos(angle - 0.4), cy + dy - 10 * Math.sin(angle - 0.4));
                head.lineTo(cx + dx - 10 * Math.cos(angle + 0.4), cy + dy - 10 * Math.sin(angle + 0.4));
                head.closePath();
                g.fill(head);
                g.setColor(Color.DARK_GRAY);
                g.drawString("wind", (float) (cx - 12), (float) (cy + len + 16));
            }

            private void drawLegend(Graphics2D g, double size, double scaleLow) {
                int x = (int) (MARGIN + size + 24), top = MARGIN, height = (int) Math.min(220, size * 0.5), width = 16;
                for (int k = 0; k < height; k++) {
                    g.setColor(color(1 - (double) k / height));
                    g.fillRect(x, top + k, width, 1);
                }
                g.setColor(Color.DARK_GRAY);
                g.drawRect(x, top, width, height);
                g.drawString("100%", x + width + 4, top + 8);
                g.drawString(String.format("%.0f%%", 100 * scaleLow), x + width + 4, top + height);
                g.drawString("power vs", x - 6, top + height + 18);
                g.drawString("no wake", x - 6, top + height + 30);
            }

            // red (low) -> yellow -> green (1 = no wake loss)
            private Color color(double t) {
                t = Math.max(0, Math.min(1, t));
                return Color.getHSBColor((float) (t / 3), 0.75f, 0.9f);
            }

            @Override
            public String getToolTipText(MouseEvent event) {
                for (int k = 0; k < cells.length; k++) {
                    double x = px(farm.centerX(cells[k])), y = py(farm.centerY(cells[k]));
                    if (event.getPoint().distance(x, y) <= 12)
                        return String.format("<html>cell %d at (%.0f m, %.0f m)<br>power %.2f kW (%.1f%% of an unwaked turbine)</html>",
                                cells[k], farm.centerX(cells[k]), farm.centerY(cells[k]), powers[k], 100 * powers[k] / freePower);
                }
                return null;
            }
        }
    }
}
