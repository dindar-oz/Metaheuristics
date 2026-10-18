package experimentrunner;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * JSON files of the runner. Writes go to a temporary file that then replaces the target, so a reader (or a crash)
 * never sees half a file.
 */
public final class Json {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping()
            .serializeSpecialFloatingPointValues().create();
    // one object per line, for the .jsonl files
    public static final Gson LINE = new GsonBuilder().disableHtmlEscaping().serializeSpecialFloatingPointValues().create();

    private Json() {
    }

    public static <T> T read(Path file, Class<T> type) {
        try {
            return GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (JsonParseException e) {
            throw new IllegalArgumentException(file + " is not valid JSON: " + e.getMessage(), e);
        }
    }

    public static void write(Path file, Object value) {
        writeString(file, GSON.toJson(value) + System.lineSeparator());
    }

    public static void writeString(Path file, String text) {
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                // Windows refuses the replace while another process reads the file: try again shortly
                sleep(50);
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
