package lab.problemlab.pecto;

import lab.problemlab.pecto.PECTOEditor.Tool;
import lab.problemlab.ui.ZoomPanCanvas;
import lab.view.PECTOVisualizer;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.ParticipantType;
import problems.pecto.Route;
import problems.pecto.Urgency;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

/**
 * The map of a PECTO instance with its solution, drawn as in {@link PECTOVisualizer}: ambulances as squares, patients
 * colored by urgency, hospitals as crosses with a ring showing how many beds are taken. It recognizes the gestures of
 * the current {@link Tool} (clicks, drags, right-clicks) and reports them to the {@link PECTOEditor}, drawing live
 * feedback while a participant is dragged.
 */
final class PECTOCanvas extends ZoomPanCanvas {

    private static final Color AMBULANCE = new Color(40, 55, 80);
    private static final Color VIOLATION = new Color(200, 0, 0);
    private static final Color SELECTION = new Color(20, 120, 255);
    private static final Color GOOD_TARGET = new Color(30, 150, 60);
    private static final Stroke DASHED = new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{5f, 4f}, 0f);
    private static final int LEGEND = 26;
    private static final int DRAG_THRESHOLD = 4;

    private final PECTOEditor editor;
    private PECTOFacts facts;
    private Route[] before;             // by ambulance: the route before the last change, null where it did not change
    private int selected = -1, route = -1, hover = -1;
    private Tool tool = Tool.SELECT;
    private boolean allRoutes = true, labels, showChanges = true;

    // the gesture in progress
    private int pressed = -1;           // participant under the mouse when the button went down
    private Point pressPoint, dragPoint;
    private boolean dragging, panning;

    PECTOCanvas(PECTOEditor editor) {
        this.editor = editor;
        setPreferredSize(new Dimension(600, 500));
        setToolTipText("");
        MouseAdapter gestures = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (e.isPopupTrigger())
                    popup(e);
                else if (SwingUtilities.isLeftMouseButton(e))
                    press(e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e))
                    drag(e.getPoint());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger())
                    popup(e);
                else if (SwingUtilities.isLeftMouseButton(e))
                    release(e.getPoint());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int id = participantAt(e.getPoint(), -1);
                if (id != hover) {
                    hover = id;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hover = -1;
                repaint();
            }
        };
        addMouseListener(gestures);
        addMouseMotionListener(gestures);
    }

    static Color urgencyColor(Urgency u) {
        return PECTOVisualizer.urgencyColor(u);
    }

    // ---- state from the editor

    void show(PECTOFacts facts, Route[] before) {
        boolean newInstance = this.facts == null || facts == null || this.facts.problem != facts.problem;
        this.facts = facts;
        this.before = before;
        if (newInstance) {
            hover = -1;
            boundsChanged();
        }
        repaint();
    }

    void setSelection(int selected, int route) {
        this.selected = selected;
        this.route = route;
        repaint();
    }

    void setTool(Tool tool) {
        this.tool = tool;
        setCursor(Cursor.getPredefinedCursor(tool == Tool.SELECT || tool == Tool.ROUTE ? Cursor.DEFAULT_CURSOR : Cursor.CROSSHAIR_CURSOR));
        repaint();
    }

    void setAllRoutes(boolean allRoutes) {
        this.allRoutes = allRoutes;
        repaint();
    }

    void setLabels(boolean labels) {
        this.labels = labels;
        repaint();
    }

    void setShowChanges(boolean showChanges) {
        this.showChanges = showChanges;
        repaint();
    }

    // ---- geometry

    // the area of the paper's instances if every participant lies in it, otherwise the participants' bounding box
    @Override
    protected Rectangle2D worldBounds() {
        double area = PECTOInstances.AREA_SIZE;
        if (facts == null)
            return new Rectangle2D.Double(0, 0, area, area);
        PECTOProblem p = facts.problem;
        double x0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y0 = Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (int id = 0; id < p.participantCount(); id++) {
            x0 = Math.min(x0, p.x(id));
            x1 = Math.max(x1, p.x(id));
            y0 = Math.min(y0, p.y(id));
            y1 = Math.max(y1, p.y(id));
        }
        if (x0 >= 0 && y0 >= 0 && x1 <= area && y1 <= area)
            return new Rectangle2D.Double(0, 0, area, area);
        double size = Math.max(Math.max(x1 - x0, y1 - y0), 1);
        return new Rectangle2D.Double(x0 - size * 0.02, y0 - size * 0.02, Math.max(x1 - x0, size * 0.1) + size * 0.04,
                Math.max(y1 - y0, size * 0.1) + size * 0.04);
    }

    @Override
    protected int bottomReserve() {
        return LEGEND;
    }

    private double px(int id) {
        return screenX(facts.problem.x(id));
    }

    private double py(int id) {
        return screenY(facts.problem.y(id));
    }

    private double patientRadius() {
        int n = facts.problem.patientCount();
        return n > 2000 ? 2.2 : n > 500 ? 2.8 : 4;
    }

    private double hospitalRadius() {
        return facts.problem.hospitalCount() > 40 ? 6 : 8;
    }

    private double ambulanceRadius() {
        return facts.problem.ambulanceCount() > 100 ? 3.2 : 4.5;
    }

    /**
     * The participant under the point, other than except: hospitals first, then ambulances, then patients, each
     * group's nearest within a few pixels (-1 if none).
     */
    int participantAt(Point point, int except) {
        if (facts == null)
            return -1;
        PECTOProblem p = facts.problem;
        int[][] groups = {
                {p.hospitalId(0), p.hospitalCount(), 10},
                {p.ambulanceId(0), p.ambulanceCount(), 7},
                {p.patientCount() == 0 ? 0 : p.patientId(0), p.patientCount(), 6}};
        for (int[] group : groups) {
            int best = -1;
            double bestDistance = group[2];
            for (int id = group[0]; id < group[0] + group[1]; id++) {
                double d = point.distance(px(id), py(id));
                if (id != except && d <= bestDistance) {
                    bestDistance = d;
                    best = id;
                }
            }
            if (best >= 0)
                return best;
        }
        return -1;
    }

    // ---- gestures

    private void press(Point point) {
        pressPoint = point;
        dragPoint = point;
        dragging = false;
        panning = false;
        if (facts == null)
            return;
        if (tool == Tool.AMBULANCE || tool == Tool.PATIENT || tool == Tool.HOSPITAL) {
            Point2D w = toWorld(point);
            editor.addParticipant(tool, w.getX(), w.getY());
            pressPoint = null;
            return;
        }
        pressed = participantAt(point, -1);
        if (pressed >= 0)
            editor.select(pressed);
        else
            panning = true;
    }

    private void drag(Point point) {
        if (pressPoint == null)
            return;
        if (panning) {
            panBy(point.x - dragPoint.x, point.y - dragPoint.y);
            dragging = true;
        } else if (pressed >= 0 && (dragging || point.distance(pressPoint) > DRAG_THRESHOLD)) {
            dragging = true;
            hover = participantAt(point, pressed);
            repaint();
        }
        dragPoint = point;
    }

    private void release(Point point) {
        if (pressPoint == null)
            return;
        if (panning && !dragging)
            editor.select(-1);                     // a click on the background
        else if (dragging && pressed >= 0) {
            if (tool == Tool.SELECT) {
                Point2D w = toWorld(point);
                editor.moveParticipant(pressed, w.getX(), w.getY(), null);
            } else if (tool == Tool.ROUTE)
                editor.link(pressed, participantAt(point, pressed));
        }
        pressPoint = null;
        pressed = -1;
        dragging = false;
        panning = false;
        repaint();
    }

    private void popup(MouseEvent e) {
        pressPoint = null;
        editor.showContextMenu(participantAt(e.getPoint(), -1), this, e.getPoint(), toWorld(e.getPoint()));
    }

    // ---- painting

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(g.getFont().deriveFont(10f));
        if (facts == null) {
            g.setColor(Color.GRAY);
            g.setFont(g.getFont().deriveFont(14f));
            g.drawString("Generate or open an instance (side panel on the left).", 30, 40);
            g.dispose();
            return;
        }
        g.setColor(new Color(232, 232, 232));
        drawWorldRect(g, worldBounds());

        if (facts.hasSolution())
            drawRoutes(g);
        drawPatients(g);
        drawHospitals(g);
        drawAmbulances(g);
        if (facts.hasSolution() && route >= 0 && route < facts.problem.ambulanceCount())
            drawSelectedRoute(g, facts.solution.route(route));
        drawMarks(g);
        drawDragFeedback(g);
        drawHint(g);
        drawLegend(g);
        if (isFocusOwner()) {
            g.setColor(new Color(150, 190, 240));
            g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);
        }
        g.dispose();
    }

    private boolean changed(int ambulance) {
        return showChanges && before != null && before[ambulance] != null;
    }

    private void drawRoutes(Graphics2D g) {
        PECTOProblem p = facts.problem;
        boolean dim = route >= 0;
        if (allRoutes) {
            g.setStroke(new BasicStroke(p.patientCount() > 1000 ? 0.7f : 1.1f));
            for (int a = 0; a < p.ambulanceCount(); a++)
                if (a != route && !changed(a)) {
                    Color c = dim ? new Color(170, 170, 170) : PECTOVisualizer.routeColor(a);
                    g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), dim ? 60 : 110));
                    g.draw(path(facts.solution.route(a)));
                }
        }
        // what the last action changed: the old route dashed, the new one thick
        for (int a = 0; a < p.ambulanceCount(); a++)
            if (changed(a)) {
                g.setColor(new Color(120, 120, 120, 170));
                g.setStroke(DASHED);
                g.draw(path(before[a]));
                if (a != route) {
                    g.setColor(PECTOVisualizer.routeColor(a));
                    g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.draw(path(facts.solution.route(a)));
                }
            }
    }

    private Path2D path(Route r) {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(px(r.ambulance()), py(r.ambulance()));
        for (int t = 0; t < r.tripCount(); t++) {
            path.lineTo(px(r.patient(t)), py(r.patient(t)));
            path.lineTo(px(r.hospital(t)), py(r.hospital(t)));
        }
        return path;
    }

    private void drawPatients(Graphics2D g) {
        PECTOProblem p = facts.problem;
        double r = patientRadius();
        g.setStroke(new BasicStroke(1.5f));
        for (int k = 0; k < p.patientCount(); k++) {
            int id = p.patientId(k);
            double x = px(id), y = py(id);
            g.setColor(urgencyColor(p.urgency(id)));
            g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            if (facts.hasSolution() && facts.visits[k] != 1) {     // not transported, or more than once
                g.setColor(VIOLATION);
                g.draw(new Ellipse2D.Double(x - r - 3, y - r - 3, 2 * r + 6, 2 * r + 6));
            }
            label(g, id, r);
        }
    }

    private void drawHospitals(Graphics2D g) {
        PECTOProblem p = facts.problem;
        double r = hospitalRadius();
        for (int k = 0; k < p.hospitalCount(); k++) {
            int id = p.hospitalId(k);
            double x = px(id), y = py(id);
            int load = facts.load(id), capacity = p.capacity(id);
            PECTOVisualizer.hospitalSymbol(g, x, y, r, load > capacity);
            if (facts.hasSolution()) {
                // a ring around the symbol fills as beds are taken
                double fill = capacity == 0 ? (load > 0 ? 1 : 0) : Math.min(1, (double) load / capacity);
                double ring = r + 3.5;
                g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(225, 225, 225));
                g.draw(new Ellipse2D.Double(x - ring, y - ring, 2 * ring, 2 * ring));
                g.setColor(load > capacity ? VIOLATION : fill >= 1 ? new Color(230, 120, 30) : GOOD_TARGET);
                g.draw(new Arc2D.Double(x - ring, y - ring, 2 * ring, 2 * ring, 90, -360 * fill, Arc2D.OPEN));
            }
            if (labels)
                label(g, id, r + 4);
        }
    }

    private void drawAmbulances(Graphics2D g) {
        double r = ambulanceRadius();
        for (int a = 0; a < facts.problem.ambulanceCount(); a++) {
            double size = a == route ? r + 2.5 : r;
            g.setColor(a == route ? PECTOVisualizer.routeColor(a).darker() : AMBULANCE);
            g.fill(new Rectangle2D.Double(px(a) - size, py(a) - size, 2 * size, 2 * size));
            label(g, a, size);
        }
    }

    // the highlighted route on top: thick, with arrows, dashed legs to the patients and trip numbers
    private void drawSelectedRoute(Graphics2D g, Route r) {
        Color color = PECTOVisualizer.routeColor(r.ambulance()).darker();
        Stroke pickup = new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{6f, 4f}, 0f);
        Stroke delivery = new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
        int from = r.ambulance();
        for (int t = 0; t < r.tripCount(); t++) {
            int patient = r.patient(t), hospital = r.hospital(t);
            g.setColor(color);
            leg(g, from, patient, pickup);
            leg(g, patient, hospital, delivery);
            g.setColor(Color.BLACK);
            g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
            g.drawString(String.valueOf(t + 1), (float) (px(patient) + 5), (float) (py(patient) + 12));
            from = hospital;
        }
    }

    private void leg(Graphics2D g, int from, int to, Stroke stroke) {
        double x0 = px(from), y0 = py(from), x1 = px(to), y1 = py(to);
        g.setStroke(stroke);
        g.draw(new Line2D.Double(x0, y0, x1, y1));
        double length = Math.hypot(x1 - x0, y1 - y0);
        if (length < 14)
            return;
        double ux = (x1 - x0) / length, uy = (y1 - y0) / length;     // arrowhead at 60% of the leg
        double ax = x0 + 0.6 * (x1 - x0), ay = y0 + 0.6 * (y1 - y0);
        Path2D.Double head = new Path2D.Double();
        head.moveTo(ax + 5 * ux, ay + 5 * uy);
        head.lineTo(ax - 5 * ux - 4 * uy, ay - 5 * uy + 4 * ux);
        head.lineTo(ax - 5 * ux + 4 * uy, ay - 5 * uy - 4 * ux);
        head.closePath();
        g.fill(head);
    }

    // the selection and the participant under the mouse
    private void drawMarks(Graphics2D g) {
        if (selected >= 0 && selected < facts.problem.participantCount()) {
            g.setColor(SELECTION);
            g.setStroke(new BasicStroke(2.2f));
            ring(g, selected, markRadius(selected) + 5);
        }
        if (hover >= 0 && hover != selected && !dragging) {
            g.setColor(new Color(20, 120, 255, 120));
            g.setStroke(new BasicStroke(1.5f));
            ring(g, hover, markRadius(hover) + 4);
        }
    }

    private double markRadius(int id) {
        return switch (facts.problem.typeOf(id)) {
            case AMBULANCE -> ambulanceRadius() + 1;
            case PATIENT -> patientRadius();
            case HOSPITAL -> hospitalRadius() + 3;
        };
    }

    private void ring(Graphics2D g, int id, double r) {
        g.draw(new Ellipse2D.Double(px(id) - r, py(id) - r, 2 * r, 2 * r));
    }

    // while dragging: a ghost where a moved participant would go, or a link to the participant it would be dropped on
    private void drawDragFeedback(Graphics2D g) {
        if (!dragging || pressed < 0 || dragPoint == null)
            return;
        double x0 = px(pressed), y0 = py(pressed);
        g.setStroke(DASHED);
        if (tool == Tool.SELECT) {
            g.setColor(Color.GRAY);
            g.draw(new Line2D.Double(x0, y0, dragPoint.x, dragPoint.y));
            Point2D w = toWorld(dragPoint);
            drawGhost(g, facts.problem.typeOf(pressed), dragPoint.x, dragPoint.y);
            caption(g, "(%.1f, %.1f)".formatted(w.getX(), w.getY()), dragPoint, Color.DARK_GRAY);
        } else if (tool == Tool.ROUTE) {
            String what = editor.linkDescription(pressed, hover);
            g.setColor(what == null ? Color.GRAY : GOOD_TARGET);
            g.draw(new Line2D.Double(x0, y0, dragPoint.x, dragPoint.y));
            if (hover >= 0 && what != null) {
                g.setStroke(new BasicStroke(2.5f));
                ring(g, hover, markRadius(hover) + 6);
            }
            caption(g, what == null ? (hover >= 0 ? "no route edit" : "drop on a hospital, ambulance or patient") : what,
                    dragPoint, what == null ? Color.GRAY : GOOD_TARGET.darker());
        }
    }

    private void drawGhost(Graphics2D g, ParticipantType type, double x, double y) {
        Composite composite = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        switch (type) {
            case AMBULANCE -> {
                double r = ambulanceRadius();
                g.setColor(AMBULANCE);
                g.fill(new Rectangle2D.Double(x - r, y - r, 2 * r, 2 * r));
            }
            case PATIENT -> {
                double r = patientRadius() + 1;
                g.setColor(urgencyColor(facts.problem.urgency(pressed)));
                g.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
            }
            case HOSPITAL -> PECTOVisualizer.hospitalSymbol(g, x, y, hospitalRadius(), false);
        }
        g.setComposite(composite);
    }

    private void caption(Graphics2D g, String text, Point at, Color color) {
        g.setFont(g.getFont().deriveFont(Font.BOLD, 11f));
        FontMetrics fm = g.getFontMetrics();
        int x = at.x + 14, y = at.y - 10;
        g.setColor(new Color(255, 255, 255, 220));
        g.fill(new RoundRectangle2D.Double(x - 4, y - fm.getAscent() - 2, fm.stringWidth(text) + 8, fm.getHeight() + 4, 6, 6));
        g.setColor(color);
        g.drawString(text, x, y);
    }

    private void label(Graphics2D g, int id, double r) {
        if (!labels)
            return;
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 10f));
        g.setColor(Color.DARK_GRAY);
        g.drawString(String.valueOf(id), (float) (px(id) + r + 1), (float) (py(id) - r));
    }

    private void drawHint(Graphics2D g) {
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        String text = tool.label + ": " + tool.hint;
        FontMetrics fm = g.getFontMetrics();
        int width = Math.min(fm.stringWidth(text) + 12, getWidth() - 16);
        g.setColor(new Color(255, 255, 255, 215));
        g.fill(new RoundRectangle2D.Double(8, 6, width, fm.getHeight() + 6, 8, 8));
        g.setColor(new Color(70, 70, 70));
        Shape clip = g.getClip();
        g.clipRect(8, 6, width - 4, fm.getHeight() + 6);
        g.drawString(text, 14, 6 + 3 + fm.getAscent());
        g.setClip(clip);
    }

    private void drawLegend(Graphics2D g) {
        int y = getHeight() - 10;
        float x = 16;
        g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
        g.setColor(AMBULANCE);
        g.fill(new Rectangle2D.Double(x, y - 8, 8, 8));
        g.setColor(Color.DARK_GRAY);
        g.drawString("ambulance", x + 12, y);
        x += 80;
        PECTOVisualizer.hospitalSymbol(g, x + 5, y - 4, 6, false);
        g.setColor(Color.DARK_GRAY);
        g.drawString("hospital (ring: beds taken)", x + 14, y);
        x += 165;
        Urgency[] levels = Urgency.values();
        for (int k = levels.length - 1; k >= 0; k--) {
            g.setColor(urgencyColor(levels[k]));
            g.fill(new Ellipse2D.Double(x, y - 8, 8, 8));
            g.setColor(Color.DARK_GRAY);
            String name = PECTOEditor.name(levels[k]);
            g.drawString(name, x + 11, y);
            x += 18 + g.getFontMetrics().stringWidth(name);
        }
        g.setColor(VIOLATION);
        g.setStroke(new BasicStroke(1.5f));
        g.draw(new Ellipse2D.Double(x, y - 9, 10, 10));
        g.setColor(Color.DARK_GRAY);
        g.drawString("violation", x + 14, y);
        x += 75;
        g.setColor(Color.GRAY);
        g.setStroke(DASHED);
        g.draw(new Line2D.Double(x, y - 4, x + 18, y - 4));
        g.setColor(Color.DARK_GRAY);
        g.drawString("before last change", x + 22, y);
    }

    // ---- tooltips

    @Override
    public String getToolTipText(MouseEvent event) {
        if (facts == null || dragging)
            return null;
        int id = participantAt(event.getPoint(), -1);
        if (id < 0)
            return null;
        PECTOProblem p = facts.problem;
        String where = "at (%.1f, %.1f)".formatted(p.x(id), p.y(id));
        return switch (p.typeOf(id)) {
            case AMBULANCE -> {
                if (!facts.hasSolution())
                    yield "<html><b>Ambulance %d</b> %s</html>".formatted(id, where);
                Route r = facts.solution.route(id);
                yield "<html><b>Ambulance %d</b> %s<br>%d trips, route cost %.2f (%.1f%% of the total)</html>".formatted(
                        id, where, r.tripCount(), facts.routeCost[id], facts.cost > 0 ? 100 * facts.routeCost[id] / facts.cost : 0);
            }
            case PATIENT -> {
                String transport;
                if (!facts.hasSolution())
                    transport = "";
                else if (facts.visitsOf(id) == 0)
                    transport = "<br><font color='red'>not transported</font>";
                else {
                    Route r = facts.solution.route(facts.carrierOf(id));
                    int t = facts.tripOf(id);
                    transport = "<br>trip %d of ambulance %d, to hospital %d<br>trip cost %.2f".formatted(
                            t + 1, r.ambulance(), r.hospital(t), facts.tripCost(r, t));
                    if (facts.visitsOf(id) > 1)
                        transport += "<br><font color='red'>transported %d times</font>".formatted(facts.visitsOf(id));
                }
                yield "<html><b>Patient %d</b> %s<br>urgency %s (weight %.2f)%s</html>".formatted(
                        id, where, PECTOEditor.name(p.urgency(id)), p.urgencyWeight(id), transport);
            }
            case HOSPITAL -> {
                int load = facts.load(id), capacity = p.capacity(id);
                yield "<html><b>Hospital %d</b> %s<br>%d of %d beds taken%s</html>".formatted(id, where, load, capacity,
                        load > capacity ? " <font color='red'>(over capacity)</font>" : "");
            }
        };
    }
}
