package experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class TextFileERH implements ExperimentResultHandler{
    private final String outputFileName;

    public TextFileERH(String outputFileName) {
        this.outputFileName = outputFileName;
    }

    @Override
    public void handle(String result) {
        if (outputFileName ==null || result == null || result.isEmpty())
            return;

        try {
            File outputFile = new File(outputFileName);
            BufferedWriter outputWriter = new BufferedWriter(new FileWriter(outputFile,true));
            outputWriter.write(result);
            outputWriter.close();

        } catch (IOException ex) {
            throw new java.io.UncheckedIOException(ex);
        }
    }
}
