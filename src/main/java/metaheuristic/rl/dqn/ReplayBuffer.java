package metaheuristic.rl.dqn;

import metaheuristic.rl.FeaturedConstruction;
import util.random.RNG;

import java.util.List;

/**
 * Experience replay: the most recent transitions up to a capacity, sampled uniformly. Confined to one run.
 */
final class ReplayBuffer {

    private final Transition[] transitions;
    private int size;
    private int next;

    ReplayBuffer(int capacity) {
        transitions = new Transition[capacity];
    }

    void addAll(List<Transition> added) {
        for (Transition t : added) {
            transitions[next] = t;
            next = (next + 1) % transitions.length;
            size = Math.min(size + 1, transitions.length);
        }
    }

    int size() {
        return size;
    }

    Transition sample(RNG rng) {
        return transitions[rng.randInt(size)];
    }

    /**
     * The feasible actions of a state, their features as rows of one float array and their known rewards (the
     * baselines of their values), and the rows of the actions valued highest when the state was visited (the
     * candidates of its bootstrap targets).
     */
    static final class Observation {
        final int[] actions;
        final float[] features;   // row k: features[k * width .. (k + 1) * width - 1], for actions[k]
        final int width;
        final double[] baselines; // value of action k = baselines[k] + scale * network output for row k
        final double scale;
        int[] candidates;         // all rows if null

        private Observation(int[] actions, float[] features, int width, double[] baselines, double scale) {
            this.actions = actions;
            this.features = features;
            this.width = width;
            this.baselines = baselines;
            this.scale = scale;
        }

        // with useRewards, the baselines are the construction's known rewards if it tells them, otherwise 0
        static Observation of(FeaturedConstruction<?> construction, int width, boolean useRewards) {
            int[] actions = construction.feasibleActions();
            if (actions.length == 0)
                throw new IllegalStateException("an incomplete construction has no feasible action");
            double[][] rows = construction.actionFeatures();
            if (rows.length != actions.length)
                throw new IllegalStateException("expected features of " + actions.length + " actions, got " + rows.length);
            float[] features = new float[actions.length * width];
            for (int k = 0; k < rows.length; k++) {
                if (rows[k].length != width)
                    throw new IllegalStateException("expected " + width + " features, got " + rows[k].length);
                for (int f = 0; f < width; f++)
                    features[k * width + f] = (float) rows[k][f];
            }
            double[] rewards = useRewards ? construction.actionRewards() : null;
            double scale = 1;
            if (rewards == null)
                rewards = new double[actions.length];
            else if (rewards.length != actions.length)
                throw new IllegalStateException("expected rewards of " + actions.length + " actions, got " + rewards.length);
            else if (!((scale = construction.valueScale()) > 0))
                throw new IllegalStateException("the value scale must be positive, was " + scale);
            return new Observation(actions, features, width, rewards, scale);
        }

        int size() {
            return actions.length;
        }

        float[] row(int k) {
            float[] r = new float[width];
            System.arraycopy(features, k * width, r, 0, width);
            return r;
        }
    }

    /**
     * An n-step transition: the features and baseline of the action taken, the discounted rewards of the next
     * (at most n) steps, the discount of the value of the state reached, and that state (null if terminal).
     */
    static final class Transition {
        final float[] features;
        final double baseline;
        final double scale;
        final double reward;
        final double discount;
        final Observation next;

        Transition(float[] features, double baseline, double scale, double reward, double discount, Observation next) {
            this.features = features;
            this.baseline = baseline;
            this.scale = scale;
            this.reward = reward;
            this.discount = discount;
            this.next = next;
        }
    }
}
