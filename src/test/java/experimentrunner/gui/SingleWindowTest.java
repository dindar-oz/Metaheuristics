package experimentrunner.gui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingleWindowTest {

    @TempDir
    Path dir;

    @Test
    void secondOpenFocusesTheFirstWindow() throws Exception {
        assertFalse(SingleWindow.focusOpen(dir), "no window yet");

        SingleWindow first = SingleWindow.claim(dir);
        assertNotNull(first);
        AtomicInteger focused = new AtomicInteger();
        first.onFocus(focused::incrementAndGet);

        assertNull(SingleWindow.claim(dir), "the window is taken");
        assertTrue(SingleWindow.focusOpen(dir));
        assertTrue(SingleWindow.focusOpen(dir));
        assertEquals(2, focused.get());

        first.close();
        assertFalse(Files.exists(dir.resolve("gui.port")));
        assertFalse(SingleWindow.focusOpen(dir), "closed");
        SingleWindow again = SingleWindow.claim(dir);
        assertNotNull(again, "free after the window closed");
        again.close();
    }

    @Test
    void aStalePortFileIsNotAWindow() throws Exception {
        Files.writeString(dir.resolve("gui.port"), "1");
        assertFalse(SingleWindow.focusOpen(dir));
        SingleWindow w = SingleWindow.claim(dir);
        assertNotNull(w);
        w.close();
    }
}
