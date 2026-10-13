package lab.view;

import problems.tsp.TSPProblem;
import representation.IntegerPermutation;
import util.MultidimensionalScaling;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Draws a TSP tour. The problem only has a distance matrix, so the cities are placed with multidimensional scaling:
 * the drawn distances are proportional to the matrix (exactly, for distances that come from points in the plane;
 * otherwise as closely as possible, and the view reports the remaining distortion). One scale is used for both axes,
 * so the picture is not stretched; the orientation of the placement is arbitrary.
 */
public final class TSPVisualizer implements SolutionVisualizer<IntegerPermutation, TSPProblem> {

    private final Map<TSPProblem, MultidimensionalScaling.Embedding> cache = new WeakHashMap<>();

    @Override
    public JComponent view(TSPProblem problem, IntegerPermutation tour) {
        return new TourPanel(problem, tour, embedding(problem));
    }

    private synchronized MultidimensionalScaling.Embedding embedding(TSPProblem problem) {
        return cache.computeIfAbsent(problem, p -> MultidimensionalScaling.embed(p.getCityCount(), p::getDistance));
    }

    private static final class TourPanel extends JPanel {
        TourPanel(TSPProblem problem, IntegerPermutation tour, MultidimensionalScaling.Embedding embedding) {
            super(new BorderLayout(0, 6));
            setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            int n = problem.getCityCount();

            String placement = embedding.stress() < 1e-6
                    ? "cities placed at distances proportional to the matrix (exact)"
                    : String.format("the matrix is not planar: placed as closely as possible (stress %.2f%%, worst distance off by %.1f%%)",
                    100 * embedding.stress(), 100 * embedding.maxDistortion());
            JLabel summary = new JLabel(String.format("<html><b>%d cities</b> &nbsp; tour length %.6f &nbsp; %s<br>%s</html>",
                    n, problem.cost(tour), problem.isFeasible(tour) ? "" : "<font color='red'>not a valid tour</font>", placement));

            TourCanvas canvas = new TourCanvas(problem, tour, embedding);
            JCheckBox labels = new JCheckBox("City numbers", n <= 60);
            labels.addActionListener(e -> canvas.setLabels(labels.isSelected()));
            canvas.setLabels(labels.isSelected());

            JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            controls.add(labels);
            controls.add(new JLabel("green = start city; hover a city for details"));

            JPanel top = new JPanel(new BorderLayout(0, 4));
            top.add(summary, BorderLayout.NORTH);
            top.add(controls, BorderLayout.SOUTH);
            add(top, BorderLayout.NORTH);
            add(canvas, BorderLayout.CENTER);
        }
    }

    private static final class TourCanvas extends JPanel {
        private static final int MARGIN = 30;

        private final TSPProblem problem;
        private final int[] tour;
        private final int[] position;   // position of each city in the tour
        private final MultidimensionalScaling.Embedding embedding;
        private final double minX, minY, rangeX, rangeY;
        private boolean labels;

        TourCanvas(TSPProblem problem, IntegerPermutation tour, MultidimensionalScaling.Embedding embedding) {
            this.problem = problem;
            this.embedding = embedding;
            this.tour = new int[tour.getLength()];
            this.position = new int[problem.getCityCount()];
            for (int k = 0; k < this.tour.length; k++) {
                this.tour[k] = tour.get(k);
                position[this.tour[k]] = k;
            }
            double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
            for (int i = 0; i < embedding.size(); i++) {
                x0 = Math.min(x0, embedding.x(i));
                x1 = Math.max(x1, embedding.x(i));
                y0 = Math.min(y0, embedding.y(i));
                y1 = Math.max(y1, embedding.y(i));
            }
            minX = x0;
            minY = y0;
            rangeX = Math.max(x1 - x0, 1e-12);
            rangeY = Math.max(y1 - y0, 1e-12);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(720, 620));
            setToolTipText("");
        }

        void setLabels(boolean labels) {
            this.labels = labels;
            repaint();
        }

        // one scale for both axes keeps the drawn distances proportional
        private double scale() {
            return Math.min((getWidth() - 2.0 * MARGIN) / rangeX, (getHeight() - 2.0 * MARGIN - 30) / rangeY);
        }

        private double px(int city) {
            double s = scale();
            return (getWidth() - rangeX * s) / 2 + (embedding.x(city) - minX) * s;
        }

        private double py(int city) {
            double s = scale();
            return (getHeight() - 30 - rangeY * s) / 2 + (embedding.y(city) - minY) * s;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = tour.length;

            g.setColor(new Color(30, 90, 160));
            g.setStroke(new BasicStroke(1.6f));
            for (int k = 0; k < n; k++) {
                int a = tour[k], b = tour[(k + 1) % n];
                g.draw(new Line2D.Double(px(a), py(a), px(b), py(b)));
            }

            double r = n > 500 ? 2 : n > 100 ? 3 : 4.5;
            g.setFont(g.getFont().deriveFont(10f));
            for (int city = 0; city < embedding.size(); city++) {
                Shape dot = new Ellipse2D.Double(px(city) - r, py(city) - r, 2 * r, 2 * r);
                g.setColor(Color.WHITE);
                g.fill(dot);
                g.setColor(new Color(40, 40, 40));
                g.draw(dot);
                if (labels) {
                    g.setColor(Color.DARK_GRAY);
                    g.drawString(String.valueOf(city), (float) (px(city) + r + 1), (float) (py(city) - r));
                }
            }
            if (n > 0) {
                int start = tour[0];
                Shape ring = new Ellipse2D.Double(px(start) - r - 3, py(start) - r - 3, 2 * r + 6, 2 * r + 6);
                g.setColor(new Color(40, 170, 70));
                g.fill(ring);
                g.setColor(new Color(20, 100, 40));
                g.draw(ring);
            }
            drawScaleBar(g);
            g.dispose();
        }

        // a bar of a round length in the units of the distance matrix
        private void drawScaleBar(Graphics2D g) {
            double s = scale();
            double target = (getWidth() - 2.0 * MARGIN) / 5 / s;
            double magnitude = Math.pow(10, Math.floor(Math.log10(target)));
            double length = magnitude;
            for (double m : new double[]{2, 5, 10})
                if (m * magnitude <= target)
                    length = m * magnitude;
            int x = MARGIN, y = getHeight() - 14, w = (int) Math.round(length * s);
            g.setColor(Color.DARK_GRAY);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(x, y, x + w, y);
            g.drawLine(x, y - 4, x, y + 4);
            g.drawLine(x + w, y - 4, x + w, y + 4);
            g.drawString(String.format("%s (distance units)", format(length)), x + w + 8, y + 4);
        }

        private static String format(double v) {
            return v >= 1 && v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf((float) v);
        }

        @Override
        public String getToolTipText(MouseEvent event) {
            for (int city = 0; city < embedding.size(); city++)
                if (event.getPoint().distance(px(city), py(city)) <= 7) {
                    int k = position[city];
                    int next = tour[(k + 1) % tour.length], previous = tour[(k - 1 + tour.length) % tour.length];
                    return String.format("<html>city %d, stop %d of %d<br>from city %d: %.4f<br>to city %d: %.4f</html>",
                            city, k + 1, tour.length, previous, problem.getDistance(previous, city), next, problem.getDistance(city, next));
                }
            return null;
        }
    }

    @Override
    public String toString() {
        return "TSP tour";
    }
}
