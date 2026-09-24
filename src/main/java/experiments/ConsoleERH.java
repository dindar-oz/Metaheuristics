package experiments;

/**
 * Prints experiment results to the standard output.
 */
public class ConsoleERH implements ExperimentResultHandler {
    @Override
    public void handle(String result) {
        System.out.print(result);
    }
}
