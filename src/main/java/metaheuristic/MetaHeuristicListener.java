package metaheuristic;

/**
 * Created by dindar.oz on 22.06.2015.
 */
public interface MetaHeuristicListener {
    void onIterationEvent(IterationEvent event);
    default void onCompleteEvent(){};
}
