package metaheuristic;

import java.io.FileWriter;
import java.io.IOException;

/**
 * Created by dindar.oz on 23.06.2015.
 */
public class FileMetaheuristicListener implements MetaHeuristicListener {

    String outputFile;

    public FileMetaheuristicListener(String outputFile) {
        this.outputFile = outputFile;
    }



    @Override
    public void onIterationEvent(IterationEvent event) {

        writeToFile(event.getIterationCount()+";"+event.getBestCost()+"\n");
        System.out.println(event.toString());
    }

    private void writeToFile(String s) {
        try {
            FileWriter writer = new FileWriter(outputFile,true);
            writer.write(s);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
