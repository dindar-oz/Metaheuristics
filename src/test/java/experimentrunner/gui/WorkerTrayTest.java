package experimentrunner.gui;

import experimentrunner.Control;
import experimentrunner.Json;
import experimentrunner.QueueState;
import experimentrunner.Workspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.image.BufferedImage;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerTrayTest {

    @TempDir
    Path dir;

    @Test
    void tooltipAndColourFollowTheQueue() {
        Workspace ws = Workspace.open(dir.resolve("ws"));
        Control c = new Control(ws);
        assertTrue(WorkerTray.describe(ws.queue(), ws).contains("idle"));
        java.awt.Color idle = WorkerTray.color(ws.queue(), ws);

        Path spec = dir.resolve("spec.json");
        Json.write(spec, experimentrunner.ExperimentSpecTest.tsp("tray-demo", 2, 1000));
        c.submit(spec);
        String text = WorkerTray.describe(ws.queue(), ws);
        assertTrue(text.contains("tray-demo") && text.contains("0/8 runs"), text);
        java.awt.Color running = WorkerTray.color(ws.queue(), ws);
        assertNotEquals(idle, running);

        c.pauseAll(true);
        assertTrue(WorkerTray.describe(ws.queue(), ws).contains("ALL PAUSED"));
        java.awt.Color paused = WorkerTray.color(ws.queue(), ws);
        assertNotEquals(running, paused);

        c.stopWorker(false);
        assertTrue(WorkerTray.describe(ws.queue(), ws).contains("stopping"));
        assertNotEquals(paused, WorkerTray.color(ws.queue(), ws));
        assertTrue(WorkerTray.describe(ws.queue(), ws).length() <= 127);
        assertEquals(QueueState.Stop.AFTER_RUNS, ws.queue().stopWorker);
    }

    @Test
    void drawsTheIcon() {
        BufferedImage img = (BufferedImage) WorkerTray.image(java.awt.Color.GREEN);
        assertEquals(32, img.getWidth());
        assertEquals(0, img.getRGB(0, 0) >>> 24);              // transparent corner
        assertEquals(0xFF, img.getRGB(16, 16) >>> 24);         // opaque centre
    }
}
