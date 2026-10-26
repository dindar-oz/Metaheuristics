package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/**
 * The pheromone as a whole: how the values of the patient pheromone tau(prev, q) and of the hospital pheromone
 * tau(q, h) are spread between tau_min and tau_max (a converged colony has most at the bounds), and, where practical,
 * the hospital pheromone of every patient as a heat map.
 */
final class PheromonePanel extends JPanel {

    private static final int BINS = 20;

    private final Html header = new Html();
    private final Chart chart = new Chart();
    private Session session;
    private PheromoneTrails.Snapshot snapshot;

    PheromonePanel() {
        super(new BorderLayout(0, 6));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        add(header, BorderLayout.NORTH);
        add(chart, BorderLayout.CENTER);
    }

    void session(Session session) {
        this.session = session;
    }

    void show(Frame f) {
        snapshot = f.pheromone();
        PheromoneTrails.Snapshot s = snapshot;
        double lo = s.tauMinPatient(), threshold = lo + PheromoneTrails.LAMBDA * (s.tauMax() - lo);
        int nodes = 0, branches = 0, atMax = 0, atMin = 0, values = 0;
        for (double[] row : s.patient()) {
            if (row.length > 0)
                nodes++;
            for (double t : row) {
                values++;
                if (t >= threshold)
                    branches++;
                if (t >= s.tauMax() * (1 - 1e-9))
                    atMax++;
                if (t <= lo * (1 + 1e-9))
                    atMin++;
            }
        }
        header.set("<b>Pheromone</b> after iteration " + (f.type() == EventType.ITERATION_DONE
                ? f.iteration() : f.iteration() - 1) + " (ants read τ<sup>α</sup>; nothing changes while they build). "
                + "τ<sub>max</sub> = " + Style.num(s.tauMax()) + ", τ<sub>min</sub> = " + Style.num(lo) + " ("
                + Style.pct(lo / s.tauMax()) + " of τ<sub>max</sub>).<br>Patient pheromone: " + values + " values, "
                + Style.pct((double) atMax / Math.max(1, values)) + " at τ<sub>max</sub>, " + Style.pct((double) atMin / Math.max(1, values))
                + " at τ<sub>min</sub>; branching factor " + Style.num(nodes == 0 ? 0 : (double) branches / nodes)
                + " (candidates per node above τ<sub>min</sub> + 5% of the range; " + session.settings.candidates()
                + " at the start, 1 when converged).");
        chart.repaint();
    }

    private final class Chart extends JComponent {

        Chart() {
            setPreferredSize(new Dimension(420, 420));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (snapshot == null)
                return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            int histH = Math.min(150, (h - 40) / 3);
            histogram(g, 0, 0, w, histH, snapshot.patient(), snapshot.tauMinPatient(), "Patient pheromone τ(prev, q) / τmax");
            histogram(g, 0, histH + 16, w, histH, snapshot.hospital(), snapshot.tauMinHospital(), "Hospital pheromone τ(q, h) / τmax");
            heatMap(g, 0, 2 * histH + 40, w, h - 2 * histH - 40);
            g.dispose();
        }

        private void histogram(Graphics2D g, int x, int y, int w, int h, double[][] values, double tauMin, String title) {
            double max = snapshot.tauMax();
            long[] bins = new long[BINS];
            long n = 0;
            for (double[] row : values)
                for (double t : row) {
                    bins[Math.min(BINS - 1, (int) (BINS * t / max))]++;
                    n++;
                }
            g.setFont(getFont().deriveFont(Font.BOLD, 11.5f));
            g.setColor(Color.DARK_GRAY);
            g.drawString(title, x, y + 12);
            int top = y + 18, bottom = y + h - 14, plotW = w - 10;
            long most = 1;
            for (long b : bins)
                most = Math.max(most, b);
            double bw = plotW / (double) BINS;
            for (int i = 0; i < BINS; i++) {
                double share = (double) bins[i] / Math.max(1, n);
                int bh = (int) Math.round((bottom - top) * bins[i] / (double) most);
                g.setColor(new Color(31, 111, 235, 200));
                g.fillRect((int) (x + i * bw) + 1, bottom - bh, (int) Math.max(1, bw - 2), bh);
                if (bins[i] > 0 && bw > 26) {
                    g.setFont(getFont().deriveFont(9f));
                    g.setColor(Color.DARK_GRAY);
                    String s = Style.pct(share);
                    g.drawString(s, (int) (x + i * bw + (bw - g.getFontMetrics().stringWidth(s)) / 2), bottom - bh - 2);
                }
            }
            g.setColor(Color.GRAY);
            g.drawLine(x, bottom, x + plotW, bottom);
            g.setFont(getFont().deriveFont(9.5f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString("0", x, bottom + 11);
            g.drawString("1 (τmax)", x + plotW - fm.stringWidth("1 (τmax)"), bottom + 11);
            int mx = x + (int) (plotW * tauMin / max);
            g.setColor(new Color(0xC2185B));
            g.drawLine(mx, top, mx, bottom);
            g.drawString("τmin", Math.min(mx + 2, x + plotW - 60), top + 9);
        }

        private void heatMap(Graphics2D g, int x, int y, int w, int h) {
            g.setFont(getFont().deriveFont(Font.BOLD, 11.5f));
            g.setColor(Color.DARK_GRAY);
            g.drawString("Hospital pheromone per patient (rows: patients, columns: hospitals)", x, y + 12);
            if (!session.practicality.heatMap()) {
                g.setFont(getFont().deriveFont(11f));
                g.drawString("Off for an instance of this size; select a patient on the map for its hospital pheromone.", x, y + 30);
                return;
            }
            PECTOProblem p = session.problem;
            double[][] v = snapshot.hospital();
            int rows = v.length, cols = p.hospitalCount();
            if (rows == 0 || h < 40)
                return;
            BufferedImage img = new BufferedImage(cols, rows, BufferedImage.TYPE_INT_RGB);
            double max = snapshot.tauMax();
            for (int k = 0; k < rows; k++)
                for (int c = 0; c < cols; c++) {
                    double s = Math.max(0, Math.min(1, v[k][c] / max));
                    int shade = (int) Math.round(255 * (1 - s));
                    img.setRGB(c, k, new Color(shade, (int) (shade * 0.9 + 25 * s), 255).getRGB());
                }
            int top = y + 18, ph = h - 22;
            int pw = Math.min(w - 10, Math.max(cols * 4, Math.min(w - 10, cols * 24)));
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(img, x, top, pw, ph, null);
            g.setColor(Color.GRAY);
            g.drawRect(x, top, pw, ph);
            int sel = session.selected();
            if (sel >= 0 && p.isPatient(sel)) {
                int row = p.index(sel);
                int ry = top + (int) ((row + 0.5) * ph / rows);
                g.setColor(new Color(0xFFB300));
                g.drawLine(x - 4, ry, x + pw + 4, ry);
            }
        }
    }
}
