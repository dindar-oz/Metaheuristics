package metaheuristic.sa;

/**
 * Created by dindar.oz on 16.06.2015.
 */
public class GeometricCooling implements CoolingSchedule{
    double alpha;

    public GeometricCooling(double alpha) {
        this.alpha = alpha;
    }

    @Override
    public double updateTemperature(double t) {
        return t*alpha;
    }

    @Override
    public void init() {

    }
}
