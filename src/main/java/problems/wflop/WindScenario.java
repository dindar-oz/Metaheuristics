package problems.wflop;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A discrete wind distribution: a set of wind states (direction, speed, probability).
 * Directions follow the meteorological convention: the direction the wind blows FROM,
 * in degrees clockwise from north (0 = north, 90 = east), as in Fig. 6 of Yu &amp; Zhang (2024).
 * Immutable.
 */
public final class WindScenario {

    private final String name;
    private final List<State> states;

    public WindScenario(String name, List<State> states) {
        if (states.isEmpty())
            throw new IllegalArgumentException("a wind scenario needs at least one state");
        double sum = 0;
        for (State s : states)
            sum += s.probability;
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("probabilities must sum to 1, was " + sum);
        this.name = name;
        this.states = Collections.unmodifiableList(new ArrayList<>(states));
    }

    // Wind Condition 1: 12 m/s from 36 equally likely directions 10, 20, ..., 360 degrees
    public static WindScenario condition1() {
        return uniform("Condition 1 (12 m/s, 36 directions)", 36, 12);
    }

    /**
     * Wind Condition 2: 36 equally likely directions 10, 20, ..., 360 degrees; the speed is 8 m/s for directions
     * in (0, 120], 12 m/s in (120, 240] and 17 m/s in (240, 360] (12 directions each).
     */
    public static WindScenario condition2() {
        List<State> states = new ArrayList<>();
        for (int k = 1; k <= 36; k++) {
            double direction = 10.0 * k;
            double speed = direction <= 120 ? 8 : direction <= 240 ? 12 : 17;
            states.add(new State(direction, speed, 1.0 / 36));
        }
        return new WindScenario("Condition 2 (8/12/17 m/s sectors, 36 directions)", states);
    }

    // count equally likely directions 360/count, 2*360/count, ..., 360 degrees with the same speed
    public static WindScenario uniform(String name, int count, double speed) {
        List<State> states = new ArrayList<>(count);
        for (int k = 1; k <= count; k++)
            states.add(new State(360.0 * k / count, speed, 1.0 / count));
        return new WindScenario(name, states);
    }

    public String name() {
        return name;
    }

    public List<State> states() {
        return states;
    }

    // E[v^3] over the wind states
    public double meanCubedSpeed() {
        double sum = 0;
        for (State s : states)
            sum += s.probability * s.speed * s.speed * s.speed;
        return sum;
    }

    @Override
    public String toString() {
        return name;
    }

    /**
     * One wind state. The unit vector (flowX, flowY) is the direction the air moves TO (x = east, y = north).
     */
    public static final class State {
        public final double direction;
        public final double speed;
        public final double probability;
        public final double flowX;
        public final double flowY;

        public State(double direction, double speed, double probability) {
            if (!(speed >= 0) || !(probability >= 0))
                throw new IllegalArgumentException("speed and probability must be non-negative");
            this.direction = direction;
            this.speed = speed;
            this.probability = probability;
            double theta = Math.toRadians(direction);
            this.flowX = -Math.sin(theta);
            this.flowY = -Math.cos(theta);
        }

        @Override
        public String toString() {
            return String.format("%.0f deg %.1f m/s p=%.4f", direction, speed, probability);
        }
    }
}
