package problems.pecto.aco.gui;

import javax.swing.JComponent;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.List;

/**
 * A roulette-wheel draw as a bar: one segment per option, as wide as its probability, and a marker at the random
 * number u. The option whose segment holds the marker is the one drawn.
 */
final class RouletteBar extends JComponent {

    record Segment(String label, double weight, Color color) {
    }

    private List<Segment> segments = List.of();
    private double u = Double.NaN;
    private int chosen = -1;

    RouletteBar() {
        setPreferredSize(new Dimension(300, 44));
        setMinimumSize(new Dimension(100, 44));
    }

    void show(List<Segment> segments, double u, int chosen) {
        this.segments = segments;
        this.u = u;
        this.chosen = chosen;
        setToolTipText(Double.isNaN(u) ? null : "rng.randDouble() = " + Style.num(u));
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int x0 = 4, y0 = 14, w = getWidth() - 8, h = getHeight() - 20;
        double sum = segments.stream().mapToDouble(Segment::weight).sum();
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        FontMetrics fm = g.getFontMetrics();
        if (!(sum > 0)) {
            g.setColor(UIManager.getColor("Label.disabledForeground"));
            g.drawString("no draw: every weight is 0", x0, y0 + h / 2 + 4);
            g.dispose();
            return;
        }
        double x = x0;
        for (int i = 0; i < segments.size(); i++) {
            Segment s = segments.get(i);
            double sw = w * s.weight() / sum;
            if (sw <= 0)
                continue;
            Color c = s.color();
            g.setColor(i == chosen ? c : new Color(c.getRed(), c.getGreen(), c.getBlue(), 110));
            g.fillRect((int) Math.round(x), y0, (int) Math.max(1, Math.round(x + sw) - Math.round(x)), h);
            g.setColor(Color.WHITE);
            g.drawLine((int) Math.round(x + sw), y0, (int) Math.round(x + sw), y0 + h);
            String text = s.label() + " " + Style.pct(s.weight() / sum);
            if (fm.stringWidth(text) + 6 < sw) {
                g.setColor(i == chosen ? Color.WHITE : new Color(30, 30, 30));
                g.drawString(text, (int) (x + 4), y0 + h / 2 + fm.getAscent() / 2 - 1);
            }
            x += sw;
        }
        if (!Double.isNaN(u)) {
            int mx = x0 + (int) Math.round(w * u);
            g.setColor(UIManager.getColor("Label.foreground"));
            g.setStroke(new BasicStroke(2f));
            g.drawLine(mx, y0 - 4, mx, y0 + h + 3);
            g.drawString("u", mx - 3, y0 - 5);
        }
        g.dispose();
    }
}
