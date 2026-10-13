package metaheuristic.sa;

/**
 * Computes the next temperature. Must be stateless (shared by concurrent runs).
 */
@FunctionalInterface
public interface CoolingSchedule {
    double updateTemperature(double temperature);
}
