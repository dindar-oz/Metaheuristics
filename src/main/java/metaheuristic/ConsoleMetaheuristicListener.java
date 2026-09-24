package metaheuristic;

/**
 * Created by dindar.oz on 23.06.2015.
 */
public class ConsoleMetaheuristicListener implements MetaHeuristicListener {
    @Override
    public void onIterationEvent(IterationEvent event) {
        System.out.println(event.toString());
    }
}
