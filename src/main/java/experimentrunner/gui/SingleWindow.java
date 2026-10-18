package experimentrunner.gui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * One Experiment Runner window per workspace. The open window holds gui.lock (the OS drops the lock when its process
 * ends, even after a crash) and listens on a loopback port written to gui.port. Opening the window again (the tray,
 * the desktop shortcut, {@code runner.cmd gui}) finds the lock taken and asks that window to come to the front instead.
 */
final class SingleWindow {

    private static final String FOCUS = "focus", OK = "ok";
    private static final int CONNECT_MILLIS = 1000, ATTEMPTS = 10, RETRY_MILLIS = 200;

    private final Path root;
    private final FileChannel channel;
    private final ServerSocket server;
    private volatile Runnable onFocus = () -> {
    };

    private SingleWindow(Path root, FileChannel channel, ServerSocket server) {
        this.root = root;
        this.channel = channel;
        this.server = server;
    }

    /** Makes this process the one with the workspace's window; null if another window holds it already. */
    static SingleWindow claim(Path root) throws IOException {
        FileChannel ch = FileChannel.open(lockFile(root), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock lock;
        try {
            lock = ch.tryLock();
        } catch (OverlappingFileLockException e) {     // the window is open in this JVM
            lock = null;
        }
        if (lock == null) {
            ch.close();
            return null;
        }
        ServerSocket server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        SingleWindow w = new SingleWindow(root, ch, server);
        Files.writeString(portFile(root), Integer.toString(server.getLocalPort()), StandardCharsets.UTF_8);
        Thread t = new Thread(w::serve, "single-window");
        t.setDaemon(true);
        t.start();
        return w;
    }

    /**
     * Asks the window open on this workspace to come to the front; true if it answered, false if no window is open (or
     * it does not answer, then the caller opens one anyway).
     */
    static boolean focusOpen(Path root) {
        for (int i = 0; i < ATTEMPTS; i++) {
            if (ask(root))
                return true;
            if (!held(root))
                return false;
            try {       // the window is starting and has not written its port yet
                Thread.sleep(RETRY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** What the window does when asked to come to the front; it runs on the listening thread. */
    void onFocus(Runnable r) {
        onFocus = r;
    }

    /** Gives the workspace's window up (when the window closes). */
    void close() {
        try {
            server.close();
            Files.deleteIfExists(portFile(root));
            channel.close();        // releases the lock
        } catch (IOException ignored) {
            // the process ends soon; the OS drops the lock then
        }
    }

    private void serve() {
        while (!server.isClosed()) {
            try (Socket s = server.accept();
                 BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                 PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8)) {
                s.setSoTimeout(CONNECT_MILLIS);
                if (FOCUS.equals(in.readLine())) {
                    onFocus.run();
                    out.println(OK);
                }
            } catch (IOException e) {
                // closed, or a caller that went away
            }
        }
    }

    private static boolean ask(Path root) {
        int port;
        try {
            port = Integer.parseInt(Files.readString(portFile(root), StandardCharsets.UTF_8).trim());
        } catch (IOException | NumberFormatException e) {
            return false;
        }
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_MILLIS);
            s.setSoTimeout(CONNECT_MILLIS);
            PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);
            out.println(FOCUS);
            return OK.equals(new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine());
        } catch (IOException e) {
            return false;
        }
    }

    // true if a window holds gui.lock
    private static boolean held(Path root) {
        try (FileChannel ch = FileChannel.open(lockFile(root), StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock = ch.tryLock();
            if (lock == null)
                return true;
            lock.release();
            return false;
        } catch (OverlappingFileLockException e) {
            return true;
        } catch (IOException e) {
            return true;        // Windows: the file is locked by another process
        }
    }

    private static Path lockFile(Path root) {
        return root.resolve("gui.lock");
    }

    private static Path portFile(Path root) {
        return root.resolve("gui.port");
    }
}
