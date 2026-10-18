package experimentrunner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CleanupTest {

    @TempDir
    Path dir;

    private static Path copy(Workspace ws, String name, boolean complete, boolean old) throws Exception {
        Path c = ws.runtimeDir().resolve(name);
        Files.createDirectories(c.resolve("classes").resolve("a"));
        Files.writeString(c.resolve("classes").resolve("a").resolve("X.class"), "x".repeat(1000));
        if (complete)
            Files.writeString(c.resolve("complete"), "");
        if (old)
            try (var files = Files.walk(c)) {
                for (Path f : files.toList())
                    Files.setLastModifiedTime(f, FileTime.fromMillis(System.currentTimeMillis() - 3_600_000));
            }
        return c;
    }

    @Test
    void keepsTheNewestAndTheWorkersCopy() throws Exception {
        Workspace ws = Workspace.open(dir.resolve("ws"));
        Path oldest = copy(ws, "r-20260101-000000-aaaa", true, true);
        Path used = copy(ws, "r-20260102-000000-bbbb", true, true);
        Path middle = copy(ws, "r-20260103-000000-cccc", true, true);
        Path newest = copy(ws, "r-20260104-000000-dddd", true, true);
        Path broken = copy(ws, "r-20260100-000000-eeee", false, true);     // an interrupted copy
        Path writing = copy(ws, "r-20260105-000000-ffff", false, false);   // being written now

        // a worker that runs from 'used': it holds worker.lock (in this JVM) and says so in worker.json
        WorkerInfo w = new WorkerInfo();
        w.runtime = used.getFileName().toString();
        w.state = "running";
        w.heartbeat = System.currentTimeMillis();
        ws.writeWorker(w);
        try (FileChannel ch = FileChannel.open(ws.workerLockFile(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = ch.lock()) {
            List<String> dry = WorkerLauncher.cleanup(ws, 1, true);
            assertTrue(dry.stream().anyMatch(l -> l.startsWith("removes r-20260101")), dry.toString());
            assertTrue(Files.exists(oldest), "a dry run removes nothing");

            List<String> report = WorkerLauncher.cleanup(ws, 1, false);
            assertFalse(Files.exists(oldest), report.toString());
            assertFalse(Files.exists(middle));
            assertFalse(Files.exists(broken));
            assertTrue(Files.exists(used), "the running worker's copy stays");
            assertTrue(Files.exists(newest), "the newest copy stays (the shortcut opens the window with it)");
            assertTrue(Files.exists(writing), "a copy being written stays");
            assertTrue(report.get(report.size() - 1).startsWith("freed"));
        }
        assertThrows(IllegalArgumentException.class, () -> WorkerLauncher.cleanup(ws, 0, true));
        // without the worker its copy goes too, keep 2 keeps the two newest complete ones
        WorkerLauncher.cleanup(ws, 2, false);
        assertTrue(Files.exists(newest));
        assertTrue(Files.exists(used));
    }
}
