package lab.problemlab.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;

/**
 * A canvas for problems drawn in the plane, with world coordinates (y grows upwards) mapped to the screen by one
 * scale for both axes, so that distances look true. The wheel zooms around the mouse, dragging with the middle
 * button pans, and subclasses can pan on other gestures with {@link #panBy}. Until the user zooms or pans, the view
 * keeps {@link #worldBounds()} fitted to the canvas; {@link #fit()} returns to that.
 */
public abstract class ZoomPanCanvas extends JPanel {

    private static final int MARGIN = 24;

    private double scale = 1;       // pixels per world unit
    private double originX;         // screen position of world x = 0
    private double originY;         // screen position of world y = 0
    private boolean fitted = true;  // follow the bounds and the size until the user zooms or pans
    private Dimension fittedSize;   // the size the last fit was made for

    protected ZoomPanCanvas() {
        setBackground(Color.WHITE);
        setFocusable(true);
        MouseAdapter navigation = new MouseAdapter() {
            private Point last;

            @Override
            public void mouseWheelMoved(MouseWheelEvent e) {
                zoom(Math.pow(1.15, -e.getPreciseWheelRotation()), e.getPoint());
            }

            @Override
            public void mousePressed(MouseEvent e) {
                last = SwingUtilities.isMiddleMouseButton(e) ? e.getPoint() : null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (last != null) {
                    panBy(e.getX() - last.x, e.getY() - last.y);
                    last = e.getPoint();
                }
            }
        };
        addMouseWheelListener(navigation);
        addMouseListener(navigation);
        addMouseMotionListener(navigation);
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (fitted)
                    fit();
            }
        });
    }

    // the part of the world to show when the view is fitted
    protected abstract Rectangle2D worldBounds();

    // space kept free at the bottom of the canvas, e.g. for a legend
    protected int bottomReserve() {
        return 0;
    }

    // ---- transform

    public double screenX(double worldX) {
        return originX + worldX * scale;
    }

    public double screenY(double worldY) {
        return originY - worldY * scale;
    }

    public Point2D toWorld(Point p) {
        return new Point2D.Double((p.x - originX) / scale, (originY - p.y) / scale);
    }

    public double scale() {
        return scale;
    }

    // ---- navigation

    // shows all of worldBounds(), and keeps doing so when the bounds or the size change
    public void fit() {
        fitted = true;
        fittedSize = getSize();
        Rectangle2D b = worldBounds();
        double w = Math.max(b.getWidth(), 1e-9), h = Math.max(b.getHeight(), 1e-9);
        double availableW = Math.max(1, getWidth() - 2.0 * MARGIN), availableH = Math.max(1, getHeight() - 2.0 * MARGIN - bottomReserve());
        scale = Math.min(availableW / w, availableH / h);
        originX = (getWidth() - w * scale) / 2 - b.getMinX() * scale;
        originY = (getHeight() - bottomReserve() + h * scale) / 2 + b.getMinY() * scale;
        repaint();
    }

    // a fitted view follows size changes even before resize events arrive, e.g. when first shown
    @Override
    protected void paintComponent(Graphics g) {
        if (fitted && !getSize().equals(fittedSize))
            fit();
        super.paintComponent(g);
    }

    // call when worldBounds() may have changed
    protected void boundsChanged() {
        if (fitted)
            fit();
    }

    public void zoom(double factor, Point around) {
        double newScale = Math.max(1e-6, Math.min(1e6, scale * factor));
        Point2D w = toWorld(around);
        scale = newScale;
        originX = around.x - w.getX() * scale;
        originY = around.y + w.getY() * scale;
        fitted = false;
        repaint();
    }

    public void zoomCenter(double factor) {
        zoom(factor, new Point(getWidth() / 2, getHeight() / 2));
    }

    public void panBy(double dx, double dy) {
        originX += dx;
        originY += dy;
        fitted = false;
        repaint();
    }

    // draws the outline of a world rectangle, e.g. the area of the instance
    protected void drawWorldRect(Graphics2D g, Rectangle2D r) {
        double x0 = screenX(r.getMinX()), x1 = screenX(r.getMaxX()), y0 = screenY(r.getMaxY()), y1 = screenY(r.getMinY());
        g.draw(new Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0));
    }
}
