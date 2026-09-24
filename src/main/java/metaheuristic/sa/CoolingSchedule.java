package metaheuristic.sa;

/**
 * Created by dindar.oz on 16.06.2015.
 */
public interface CoolingSchedule {
    double updateTemperature(double t);
    void init();
}
