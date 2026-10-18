package experimentrunner.gui;

import experimentrunner.Cli;
import experimentrunner.Control;
import experimentrunner.ExperimentStatus;
import experimentrunner.QueueState;
import experimentrunner.Workspace;

import javax.swing.JOptionPane;
import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * The worker's icon in the system tray: shown while the worker runs, gone when it stops. The tooltip tells the
 * progress; double-click opens the Experiment Runner window (or brings the open one to the front); the menu pauses or resumes all experiments and stops the
 * worker. Like the window, it only reads the workspace's files and changes them through {@link Control}.
 */
public final class WorkerTray {

    private static final long REFRESH_MILLIS = 3000;
    private static final Color RUNNING = new Color(0x2da44e), PAUSED = new Color(0xd4a72c), STOPPING = new Color(0x8c959f),
            IDLE = new Color(0x54aeff);

    private final Workspace ws;
    private final Control control;
    private final TrayIcon icon;
    private final MenuItem pauseAll = new MenuItem("Pause all");
    private final Timer timer = new Timer("worker-tray", true);
    private Color shown;

    private WorkerTray(Workspace ws) throws AWTException {
        this.ws = ws;
        this.control = new Control(ws);
        PopupMenu menu = new PopupMenu();
        MenuItem open = new MenuItem("Open Experiment Runner");
        MenuItem stop = new MenuItem("Stop after current runs");
        MenuItem stopNow = new MenuItem("Stop now…");
        menu.add(open);
        menu.addSeparator();
        menu.add(pauseAll);
        menu.addSeparator();
        menu.add(stop);
        menu.add(stopNow);
        icon = new TrayIcon(image(IDLE), "Experiment worker", menu);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> openWindow());      // double-click
        open.addActionListener(e -> openWindow());
        pauseAll.addActionListener(e -> act(() -> control.pauseAll(!ws.queue().paused)));
        stop.addActionListener(e -> act(() -> control.stopWorker(false)));
        stopNow.addActionListener(e -> {
            if (JOptionPane.showConfirmDialog(null, "Stop the worker now? Its runs in flight are dropped and run again "
                    + "when it starts next.", "Experiment worker", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION)
                act(() -> control.stopWorker(true));
        });
        SystemTray.getSystemTray().add(icon);
        refresh();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                refresh();
            }
        }, REFRESH_MILLIS, REFRESH_MILLIS);
    }

    /**
     * Shows the icon if this machine has a tray (a desktop session); otherwise returns a handle that does nothing, so
     * the worker runs the same without it.
     */
    public static Runnable install(Workspace ws) {
        try {
            if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported())
                return () -> {
                };
            WorkerTray tray = new WorkerTray(ws);
            return tray::remove;
        } catch (Throwable e) {     // no desktop, a broken AWT: the tray is a convenience
            System.out.println("no tray icon: " + e);
            return () -> {
            };
        }
    }

    private void remove() {
        timer.cancel();
        SystemTray.getSystemTray().remove(icon);
    }

    private void act(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            icon.displayMessage("Experiment worker", e.getMessage(), TrayIcon.MessageType.WARNING);
        }
        refresh();
    }

    // the Experiment Runner window: the open one to the front, else a new one as a process of its own that outlives
    // the worker; off the event thread, since asking the open window waits for its answer
    private void openWindow() {
        new Thread(() -> {
            if (!SingleWindow.focusOpen(ws.root()))
                startWindow();
        }, "open-window").start();
    }

    private void startWindow() {
        Path javaw = Path.of(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "javaw.exe" : "java");
        try {
            new ProcessBuilder(javaw.toString(), "-cp", System.getProperty("java.class.path"),
                    ExperimentRunnerApp.class.getName(), ws.root().toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectErrorStream(true).start();
        } catch (IOException e) {
            icon.displayMessage("Experiment worker", "Cannot open the window: " + e.getMessage(), TrayIcon.MessageType.ERROR);
        }
    }

    private void refresh() {
        try {
            QueueState q = ws.queue();
            pauseAll.setLabel(q.paused ? "Resume all" : "Pause all");
            icon.setToolTip(describe(q, ws));
            Color c = color(q, ws);
            if (!c.equals(shown)) {
                icon.setImage(image(c));
                shown = c;
            }
        } catch (RuntimeException ignored) {
            // the files are being replaced; next time
        }
    }

    static Color color(QueueState q, Workspace ws) {
        if (q.stopWorker != null)
            return STOPPING;
        if (q.paused)
            return PAUSED;
        boolean queued = q.experiments.stream().anyMatch(e -> e.state == QueueState.State.QUEUED);
        return queued ? RUNNING : IDLE;
    }

    /** The tooltip: the experiment running (or the state of the queue) with its progress; at most 127 characters. */
    static String describe(QueueState q, Workspace ws) {
        String text;
        if (q.stopWorker != null)
            text = "Experiment worker · stopping";
        else {
            QueueState.Entry current = q.experiments.stream().filter(e -> e.state == QueueState.State.QUEUED).findFirst().orElse(null);
            if (current == null)
                text = "Experiment worker · idle, nothing queued";
            else {
                ExperimentStatus s = ws.status(current.id);
                text = "Experiment worker" + (q.paused ? " · ALL PAUSED" : "") + "\n" + current.id
                        + (s == null ? "" : String.format(Locale.ROOT, "\n%d/%d runs · %d in flight · %s left", s.done, s.total,
                        s.running, Cli.durationText(s.etaMillis)));
            }
        }
        return text.length() <= 127 ? text : text.substring(0, 126) + "…";
    }

    // a filled circle with a light ring, drawn for the tray's size
    static Image image(Color color) {
        return image(color, 32);
    }

    /** The runner's icon (a green dot) at the given size; also the desktop shortcut's icon. */
    public static BufferedImage icon(int size) {
        return (BufferedImage) image(RUNNING, size);
    }

    static Image image(Color color, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(color);
        float u = size / 32f;
        int m = Math.round(3 * u), dot = Math.round(8 * u);
        g.fillOval(m, m, size - 2 * m, size - 2 * m);
        g.setColor(new Color(255, 255, 255, 200));
        g.setStroke(new BasicStroke(2.5f * u));
        g.drawOval(m, m, size - 2 * m, size - 2 * m);
        g.setColor(Color.WHITE);
        g.fillOval(size / 2 - dot / 2, size / 2 - dot / 2, dot, dot);
        g.dispose();
        return img;
    }
}
