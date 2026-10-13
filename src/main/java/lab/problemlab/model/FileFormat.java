package lab.problemlab.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * How the Problem Lab saves and opens instances (context Void) or solutions (context: their instance).
 *
 * @param <C> what is needed besides the file, e.g. the instance a solution belongs to
 * @param <T> what is stored
 */
public interface FileFormat<C, T> {

    // e.g. "PECTO instance"
    String description();

    // the file name extension, without the dot
    String extension();

    // Throws IOException if the file cannot be read, IllegalArgumentException if its content is not valid
    T read(C context, Path file) throws IOException;

    void write(C context, T value, Path file) throws IOException;

    @FunctionalInterface
    interface TextReader<C, T> {
        T read(C context, BufferedReader in) throws IOException;
    }

    @FunctionalInterface
    interface TextWriter<C, T> {
        void write(C context, T value, Writer out) throws IOException;
    }

    // a UTF-8 text format
    static <C, T> FileFormat<C, T> text(String description, String extension, TextReader<C, T> reader, TextWriter<C, T> writer) {
        return new FileFormat<C, T>() {
            @Override
            public String description() {
                return description;
            }

            @Override
            public String extension() {
                return extension;
            }

            @Override
            public T read(C context, Path file) throws IOException {
                try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    return reader.read(context, in);
                }
            }

            @Override
            public void write(C context, T value, Path file) throws IOException {
                try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                    writer.write(context, value, out);
                }
            }
        };
    }
}
