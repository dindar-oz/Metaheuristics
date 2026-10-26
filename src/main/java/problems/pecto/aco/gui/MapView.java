package problems.pecto.aco.gui;

import lab.problemlab.ui.ZoomPanCanvas;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.Route;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * The instance on a map, with the state of the run at the current event: the routes of the ant being built (trips
 * solid, provisional deliveries dashed) or of the best solution, the decision being made (the options with their
 * probabilities), the pheromone, and the selected participant with its candidates. Wheel zooms, dragging pans, a
 * click selects.
 */
final class MapView extends ZoomPanCanvas {

    private static final Stroke DASHED = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
            new float[]{5f, 4f}, 0f);
    private static final Stroke DOTTED = new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
            new float[]{1.5f, 3.5f}, 0f);

    private Session session;
    private Frame frame;
    boolean showBest = true, showConstruction = true, showPheromone = false, showCandidates = true, showLabels = true;

    MapView() {
        MouseAdapter mouse = new MouseAdapter() {
            private Point pressed, last;
            private boolean dragged;

            @Override
            public void mousePressed(MouseEvent e) {
                pressed = last = e.getPoint();
                dragged = false;
                requestFocusInWindow();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressed == null || !javax.swing.SwingUtilities.isLeftMouseButton(e))
                    return;
                if (!dragged && e.getPoint().distance(pressed) < 4)
                    return;
                dragged = true;
                panBy(e.getX() - last.x, e.getY() - last.y);
                last = e.getPoint();
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (session != null && !dragged && javax.swing.SwingUtilities.isLeftMouseButton(e))
                    session.select(nearest(e.getPoint()));
                pressed = null;
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int id = session == null ? -1 : nearest(e.getPoint());
                setToolTipText(id < 0 ? null : tooltip(id));
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        setToolTipText("");
    }

    void session(Session session) {
        this.session = session;
        this.frame = null;
        showLabels = session.practicality.labels();
        boundsChanged();
        fit();
    }

    void show(Frame frame) {
        this.frame = frame;
        repaint();
    }

    @Override
    protected Rectangle2D worldBounds() {
        if (session == null)
            return new Rectangle2D.Double(0, 0, 1, 1);
        PECTOProblem p = session.problem;
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (int id = 0; id < p.participantCount(); id++) {
            x0 = Math.min(x0, p.x(id));
            x1 = Math.max(x1, p.x(id));
            y0 = Math.min(y0, p.y(id));
            y1 = Math.max(y1, p.y(id));
        }
        double m = 0.04 * Math.max(x1 - x0, y1 - y0) + 1e-9;
        return new Rectangle2D.Double(x0 - m, y0 - m, x1 - x0 + 2 * m, y1 - y0 + 2 * m);
    }

    // ---- painting

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        if (session == null) {
            g0.setColor(Style.MUTED);
            g0.drawString("Set up an instance and press Start.", 20, 30);
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        if (frame != null && showPheromone)
            paintPheromone(g);
        if (frame != null && showBest && (frame.construction() == null || !showConstruction))
            paintSolution(g, frame.best(), true);
        else if (frame != null && showBest)
            paintSolution(g, frame.best(), false);
        if (frame != null && showConstruction && frame.construction() != null)
            paintConstruction(g, frame.construction());
        if (showCandidates && session.selected() >= 0)
            paintCandidates(g, session.selected());
        if (frame != null)
            paintDecision(g);
        paintParticipants(g);
        g.dispose();
    }

    private double sx(int id) {
        return screenX(session.problem.x(id));
    }

    private double sy(int id) {
        return screenY(session.problem.y(id));
    }

    private void line(Graphics2D g, int i, int j) {
        g.draw(new Line2D.Double(sx(i), sy(i), sx(j), sy(j)));
    }

    private void arrow(Graphics2D g, int i, int j) {
        double x1 = sx(i), y1 = sy(i), x2 = sx(j), y2 = sy(j);
        g.draw(new Line2D.Double(x1, y1, x2, y2));
        double len = Math.hypot(x2 - x1, y2 - y1);
        if (len < 18)
            return;
        double ux = (x2 - x1) / len, uy = (y2 - y1) / len, mx = x1 + (x2 - x1) * 0.55, my = y1 + (y2 - y1) * 0.55;
        Path2D head = new Path2D.Double();
        head.moveTo(mx + ux * 4, my + uy * 4);
        head.lineTo(mx - ux * 4 - uy * 3.5, my - uy * 4 + ux * 3.5);
        head.lineTo(mx - ux * 4 + uy * 3.5, my - uy * 4 - ux * 3.5);
        head.closePath();
        g.fill(head);
    }

    // the best solution so far: in route colors when nothing else is shown, else as a light background
    private void paintSolution(Graphics2D g, PECTOSolution s, boolean prominent) {
        g.setStroke(new BasicStroke(prominent ? 1.4f : 1f));
        for (Route r : s.routes()) {
            g.setColor(prominent ? Style.ambulance(r.ambulance()) : new Color(190, 196, 204));
            int from = r.ambulance();
            for (int t = 0; t < r.tripCount(); t++) {
                if (prominent)
                    arrow(g, from, r.patient(t));
                else
                    line(g, from, r.patient(t));
                line(g, r.patient(t), r.hospital(t));
                from = r.hospital(t);
            }
        }
    }

    private void paintConstruction(Graphics2D g, Frame.Construction c) {
        for (int a = 0; a < c.stops().length; a++) {
            g.setColor(Style.ambulance(a));
            g.setStroke(new BasicStroke(1.8f));
            int from = a;
            int[] st = c.stops()[a];
            for (int k = 0; k < st.length; k += 2) {
                arrow(g, from, st[k]);
                line(g, st[k], st[k + 1]);
                from = st[k + 1];
            }
            if (c.pending()[a] >= 0) {
                arrow(g, from, c.pending()[a]);
                g.setStroke(DASHED);
                line(g, c.pending()[a], c.pendingHospital()[a]);
            }
        }
    }

    // the patient pheromone edges (prev -> q) that the colony has reinforced: strength runs from the weakest value now
    // (0) to the strongest (1), so that early on, when everything is still far above tau_min, the learned choices
    // stand out; edges below a third of the range are not drawn
    private void paintPheromone(Graphics2D g) {
        PheromoneTrails.Snapshot ph = frame.pheromone();
        int[][] cand = session.candidates.patients;
        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        for (double[] row : ph.patient())
            for (double t : row) {
                lo = Math.min(lo, t);
                hi = Math.max(hi, t);
            }
        if (!(hi > lo))
            return;     // all equal: nothing learned yet
        double span = hi - lo;
        List<double[]> edges = new ArrayList<>();       // {strength, from, to}
        for (int n = 0; n < cand.length; n++)
            for (int j = 0; j < cand[n].length; j++) {
                double s = (ph.patient()[n][j] - lo) / span;
                if (s >= 1 / 3.0)
                    edges.add(new double[]{s, n, cand[n][j]});
            }
        int limit = session.practicality.edgeLimit();
        if (edges.size() > limit) {
            edges.sort((x, y) -> Double.compare(y[0], x[0]));
            edges = edges.subList(0, limit);
        }
        Composite old = g.getComposite();
        g.setColor(Style.PHEROMONE);
        for (double[] e : edges) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.min(0.85, 0.08 + 0.75 * e[0])));
            g.setStroke(new BasicStroke((float) (0.6 + 2.6 * e[0])));
            arrow(g, (int) e[1], (int) e[2]);
        }
        g.setComposite(old);
    }

    // the candidates of the selected ambulance or patient, with their pheromone when there is room for labels
    private void paintCandidates(Graphics2D g, int id) {
        PECTOProblem p = session.problem;
        if (!p.isAmbulance(id) && !p.isPatient(id))
            return;
        int[] c = session.candidates.patients[id];
        g.setColor(Style.SELECTION);
        g.setStroke(DOTTED);
        for (int q : c)
            line(g, id, q);
        if (frame == null || !session.practicality.labels())
            return;
        g.setFont(getFont().deriveFont(10f));
        double tauMax = frame.pheromone().tauMax();
        for (int j = 0; j < c.length; j++)
            label(g, id, c[j], String.format("%.2f", frame.pheromone().patient()[id][j] / tauMax), Style.PHEROMONE);
    }

    // the options of the decision of this event, with their probabilities
    private void paintDecision(Graphics2D g) {
        Object d = frame.decision();
        g.setFont(getFont().deriveFont(Font.BOLD, 11f));
        if (d instanceof Frame.AmbulanceDecision ad) {
            Frame.Construction c = frame.construction();
            g.setStroke(new BasicStroke(2.2f));
            for (int i = 0; i < ad.shortlist().length; i++) {
                int a = ad.shortlist()[i];
                ring(g, a, a == ad.chosen() ? Style.SELECTION : Style.HIGHLIGHT, 11);
                if (c != null && c.rated()[a] >= 0) {
                    g.setColor(Style.ambulance(a));
                    g.setStroke(DOTTED);
                    line(g, c.at()[a], c.rated()[a]);
                    g.setStroke(new BasicStroke(2.2f));
                }
            }
        } else if (d instanceof Frame.PatientDecision pd) {
            double sum = pd.sum();
            for (Frame.Option o : pd.options()) {
                if (!o.available() || !(sum > 0))
                    continue;
                double pr = o.weight() / sum;
                g.setColor(Style.ambulance(pd.ambulance()));
                g.setStroke(new BasicStroke((float) (1 + 4 * pr), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                        new float[]{2f, 4f}, 0f));
                line(g, pd.at(), o.id());
                if (session.practicality.small())
                    label(g, pd.at(), o.id(), Style.pct(pr), Style.ambulance(pd.ambulance()));
            }
            ring(g, pd.chosen(), Style.SELECTION, 10);
        } else if (d instanceof Frame.HospitalDecision hd) {
            for (Frame.HospitalOption o : hd.options()) {
                if (!o.considered() || !(hd.sum() > 0))
                    continue;
                double pr = o.weight() / hd.sum();
                g.setColor(Style.HOSPITAL);
                g.setStroke(new BasicStroke((float) (1 + 4 * pr), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
                        new float[]{2f, 4f}, 0f));
                line(g, hd.patient(), o.h());
                if (!hd.provisional()) {
                    g.setStroke(DOTTED);
                    line(g, o.h(), hd.next());
                }
                label(g, hd.patient(), o.h(), Style.pct(pr), Style.HOSPITAL);
            }
            ring(g, hd.chosen(), Style.SELECTION, 12);
            ring(g, hd.patient(), Style.HIGHLIGHT, 9);
        } else if (d instanceof Frame.StepDone sd) {
            ring(g, sd.patient(), Style.SELECTION, 9);
            ring(g, sd.ambulance(), Style.SELECTION, 10);
        }
    }

    private void ring(Graphics2D g, int id, Color color, double r) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2f));
        g.draw(new Ellipse2D.Double(sx(id) - r, sy(id) - r, 2 * r, 2 * r));
    }

    private void label(Graphics2D g, int from, int to, String text, Color color) {
        double x = sx(from) + (sx(to) - sx(from)) * 0.6, y = sy(from) + (sy(to) - sy(from)) * 0.6;
        int w = g.getFontMetrics().stringWidth(text) + 6, h = g.getFontMetrics().getHeight();
        g.setColor(new Color(255, 255, 255, 220));
        g.fillRoundRect((int) x - w / 2, (int) y - h / 2, w, h, 6, 6);
        g.setColor(color);
        g.drawString(text, (int) x - w / 2 + 3, (int) y + g.getFontMetrics().getAscent() / 2 - 1);
    }

    private void paintParticipants(Graphics2D g) {
        PECTOProblem p = session.problem;
        boolean small = session.practicality.small();
        double r = small ? 5 : 2.6;
        Frame.Construction c = frame == null ? null : frame.construction();
        g.setFont(getFont().deriveFont(10.5f));
        g.setStroke(new BasicStroke(1f));
        for (int k = 0; k < p.patientCount(); k++) {
            int id = p.patientId(k);
            Color col = Style.urgency(p.urgency(id));
            boolean served = c != null && c.served()[k];
            Ellipse2D dot = new Ellipse2D.Double(sx(id) - r, sy(id) - r, 2 * r, 2 * r);
            g.setColor(served ? Color.WHITE : col);
            g.fill(dot);
            g.setColor(col);
            g.draw(dot);
        }
        double hs = small ? 6 : 4;
        for (int k = 0; k < p.hospitalCount(); k++) {
            int id = p.hospitalId(k);
            double x = sx(id), y = sy(id);
            g.setColor(Style.HOSPITAL);
            g.fill(new Rectangle2D.Double(x - hs / 3, y - hs, 2 * hs / 3, 2 * hs));
            g.fill(new Rectangle2D.Double(x - hs, y - hs / 3, 2 * hs, 2 * hs / 3));
            if (c != null && small) {
                int cap = p.capacity(id), used = cap - c.room()[k];
                double bw = 26, bh = 4, bx = x - bw / 2, by = y + hs + 3;
                g.setColor(new Color(255, 255, 255, 230));
                g.fill(new Rectangle2D.Double(bx, by, bw, bh));
                g.setColor(Style.HOSPITAL);
                g.fill(new Rectangle2D.Double(bx, by, cap == 0 ? 0 : bw * used / cap, bh));
                g.draw(new Rectangle2D.Double(bx, by, bw, bh));
            }
        }
        double as = small ? 9 : 5;
        for (int a = 0; a < p.ambulanceCount(); a++) {
            g.setColor(Style.ambulance(a));
            g.fill(new Rectangle2D.Double(sx(a) - as / 2, sy(a) - as / 2, as, as));
            g.setColor(Color.WHITE);
            g.draw(new Rectangle2D.Double(sx(a) - as / 2, sy(a) - as / 2, as, as));
        }
        if (showLabels) {
            g.setColor(new Color(40, 44, 52));
            for (int id = 0; id < p.participantCount(); id++)
                g.drawString(session.label(id), (float) sx(id) + 6, (float) sy(id) - 5);
        }
        int sel = session.selected();
        if (sel >= 0) {
            g.setColor(Style.SELECTION);
            g.setStroke(new BasicStroke(2.4f));
            double rr = small ? 10 : 7;
            g.draw(new Ellipse2D.Double(sx(sel) - rr, sy(sel) - rr, 2 * rr, 2 * rr));
        }
    }

    // ---- picking

    private int nearest(Point pt) {
        PECTOProblem p = session.problem;
        int best = -1;
        double bestD = 12;
        for (int id = 0; id < p.participantCount(); id++) {
            double d = Math.hypot(sx(id) - pt.x, sy(id) - pt.y);
            if (d < bestD) {
                bestD = d;
                best = id;
            }
        }
        return best;
    }

    private String tooltip(int id) {
        PECTOProblem p = session.problem;
        StringBuilder sb = new StringBuilder("<html><b>").append(session.label(id)).append("</b>");
        if (p.isPatient(id))
            sb.append(" · ").append(p.urgency(id).name().toLowerCase()).append(", w = ").append(p.urgencyWeight(id));
        if (p.isHospital(id))
            sb.append(" · ").append(p.capacity(id)).append(" beds");
        Frame.Construction c = frame == null ? null : frame.construction();
        if (c != null && p.isAmbulance(id))
            sb.append("<br>").append(Style.state(c.rateState()[id])).append(c.rateState()[id] == DiagnosticAnt.UNKNOWN ? ""
                    : " rating " + Style.num(c.rating()[id]) + " by " + session.label(c.rated()[id]))
                    .append("<br>at ").append(session.label(c.at()[id])).append(", ").append(c.stops()[id].length / 2)
                    .append(" trips").append(c.pending()[id] >= 0 ? " + " + session.label(c.pending()[id]) : "");
        if (c != null && p.isHospital(id))
            sb.append("<br>free beds: ").append(c.room()[p.index(id)]);
        return sb.append("<br><i>click to select</i></html>").toString();
    }
}
