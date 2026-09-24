package metaheuristic.sa;

/**
 * Created by dindar.oz on 16.06.2015.
 */
public class LinearCooling implements CoolingSchedule {
    double delta;

    public LinearCooling(double delta) {
        this.delta = delta;
    }

    @Override
    public double updateTemperature(double t) {
        return t-delta;
    }

    @Override
    public void init() {

    }
}
