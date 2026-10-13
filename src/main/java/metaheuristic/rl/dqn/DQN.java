package metaheuristic.rl.dqn;

import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Metaheuristic;
import metaheuristic.Params;
import metaheuristic.rl.FeaturedConstruction;
import metaheuristic.rl.FeaturedConstructiveProblem;
import metaheuristic.rl.dqn.ReplayBuffer.Observation;
import metaheuristic.rl.dqn.ReplayBuffer.Transition;
import metaheuristic.rl.nn.Adam;
import metaheuristic.rl.nn.Mlp;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import representation.base.Representation;
import util.random.RNG;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Deep Q-learning (Mnih et al. 2015) as a constructive metaheuristic. Every episode builds one solution of a
 * FeaturedConstructiveProblem, taking each decision by an Exploration policy over the learned action value
 * Q(s, a) = f(features(s, a)) of a shared network f; the result is the best solution of all episodes.
 * The network is trained while searching, with
 * <ul>
 *   <li>experience replay: n-step transitions of past episodes, sampled uniformly;</li>
 *   <li>elite replay: a fraction of every batch comes from the episode that built the best solution so far;</li>
 *   <li>a target network, synchronized every targetUpdate steps;</li>
 *   <li>double Q-learning targets: the online network picks the next action, the target network values it;
 *       the next action is picked among the candidate actions of the state: those the online network valued highest
 *       when the state was visited (as in Wolpertinger, Dulac-Arnold et al. 2015), which spares valuing every action
 *       of every sampled state;</li>
 *   <li>the Huber loss and Adam.</li>
 * </ul>
 * When the problem tells the reward of every action in advance, Q(s, a) = reward(s, a) + scale(s) f(features(s, a)): the network
 * learns only the value of what follows an action (the foresight), and starts at 0, i.e. as the greedy heuristic of
 * taking the best immediate reward.
 * One iteration is one episode, which costs one evaluation. Exploration is softmax over the action values with a
 * decaying temperature (default) or epsilon-greedy with a decaying epsilon, and episodes 0, greedyEvery, 2 greedyEvery,
 * ... are greedy: the first one shows the policy a run starts from (with a reward baseline and a new network, the greedy
 * heuristic), so the result is never worse than that.
 * Networks, replay and optimizer state are local to search(), so one instance can be run any number of times.
 * <p>
 * Knowledge can be carried between problems: a run can start from a given network (e.g. one trained on a smaller
 * instance) instead of a random one, keep training it or only use it (training(false)), and hand its final network
 * to a consumer, e.g. to save it.
 * <pre>
 * DQN&lt;CellLayout&gt; dqn = DQN.&lt;CellLayout&gt;builder().hidden(16, 16).nStep(3).build();
 * </pre>
 */
public final class DQN<S extends Representation<S>> extends AbstractMetaheuristic<S, FeaturedConstructiveProblem<S>> {

    public static final String EPSILON = "epsilon";
    public static final String TEMPERATURE = "temperature";
    public static final String LOSS = "loss";

    private static final double MAX_GRADIENT_NORM = 10;

    private final int[] hidden;
    private final double learningRate;
    private final double gamma;
    private final int nStep;
    private final int batchSize;
    private final int replayCapacity;
    private final int warmup;
    private final int trainEvery;
    private final int targetUpdate;
    private final Exploration exploration;
    private final double eliteFraction;
    private final int greedyEvery;
    private final int candidates;
    private final Mlp initialNetwork;              // null: a random one
    private final boolean training;
    private final Consumer<Mlp> trainedNetwork;     // null: none
    private final boolean rewardBaseline;

    private DQN(Builder<S> b) {
        super(b.name);
        this.hidden = b.hidden.clone();
        for (int h : hidden)
            Params.positive("hidden layer size", h);
        this.learningRate = Params.positive("learningRate", b.learningRate);
        this.gamma = Params.probability("gamma", b.gamma);
        this.nStep = Params.positive("nStep", b.nStep);
        this.batchSize = Params.positive("batchSize", b.batchSize);
        this.replayCapacity = Params.positive("replayCapacity", b.replayCapacity);
        this.warmup = Params.positive("warmup", b.warmup);
        this.trainEvery = Params.positive("trainEvery", b.trainEvery);
        this.targetUpdate = Params.positive("targetUpdate", b.targetUpdate);
        this.exploration = Params.required("exploration", b.exploration);
        this.eliteFraction = Params.probability("eliteFraction", b.eliteFraction);
        if (b.greedyEvery < 0)
            throw new IllegalArgumentException("greedyEvery must not be negative, was " + b.greedyEvery);
        this.greedyEvery = b.greedyEvery;
        if (b.candidates < 0)
            throw new IllegalArgumentException("candidates must not be negative, was " + b.candidates);
        this.candidates = b.candidates;
        this.initialNetwork = b.initialNetwork == null ? null : b.initialNetwork.copy();
        this.training = b.training;
        this.trainedNetwork = b.trainedNetwork;
        this.rewardBaseline = b.rewardBaseline;
    }

    public static <S extends Representation<S>> Builder<S> builder() {
        return new Builder<>();
    }

    @Override
    public void search(FeaturedConstructiveProblem<S> problem, SearchContext<S> ctx) {
        int width = problem.featureCount();
        // learning Q - reward, a zero network starts as the greedy choice of the best known reward
        Mlp online = initialNetwork != null ? initialNetwork.copy() : new Mlp(width, hidden, ctx.rng(), rewardBaseline ? 0 : 1);
        if (online.inputCount() != width)
            throw new IllegalArgumentException("the network takes " + online.inputCount() + " features, the problem has " + width);
        try {
            search(problem, ctx, online);
        } finally {
            if (trainedNetwork != null)
                trainedNetwork.accept(online.copy());
        }
    }

    private void search(FeaturedConstructiveProblem<S> problem, SearchContext<S> ctx, Mlp online) {
        RNG rng = ctx.rng();
        int width = problem.featureCount();
        Mlp target = online.copy();
        Adam adam = new Adam(online, learningRate, MAX_GRADIENT_NORM);
        Mlp.Gradients gradients = online.newGradients();
        ReplayBuffer replay = new ReplayBuffer(replayCapacity);
        List<Transition> elite = Collections.emptyList();
        double eliteCost = Double.POSITIVE_INFINITY;
        long steps = 0;

        for (int episode = 0; !ctx.shouldStop(); episode++) {
            boolean greedy = greedyEvery > 0 && episode % greedyEvery == 0;
            double level = greedy ? 0 : exploration.level(episode);
            FeaturedConstruction<S> construction = problem.start(rng);
            List<float[]> taken = new ArrayList<>();
            List<Double> baselines = new ArrayList<>();
            List<Double> scales = new ArrayList<>();
            List<Double> rewards = new ArrayList<>();
            List<Observation> reached = new ArrayList<>();
            double lossSum = 0;
            int updates = 0;

            Observation observation = observe(construction, width);
            while (observation != null) {
                if (ctx.shouldStop())
                    return;
                double[] values = values(online, observation);
                if (candidates > 0)
                    observation.candidates = top(values, candidates);
                int k = greedy ? argmax(values) : exploration.choose(values, level, rng);
                taken.add(observation.row(k));
                baselines.add(observation.baselines[k]);
                scales.add(observation.scale);
                rewards.add(construction.step(observation.actions[k]));
                observation = observe(construction, width);
                reached.add(observation);

                steps++;
                if (!training)
                    continue;
                if (steps % trainEvery == 0 && replay.size() >= warmup) {
                    lossSum += train(online, target, adam, gradients, replay, elite, rng);
                    updates++;
                }
                if (steps % targetUpdate == 0)
                    target.copyFrom(online);
            }

            double cost = ctx.evaluate(construction.solution());
            if (training) {
                List<Transition> transitions = transitions(taken, baselines, scales, rewards, reached);
                replay.addAll(transitions);
                if (cost < eliteCost) {
                    eliteCost = cost;
                    elite = transitions;
                }
            }
            ctx.iterationCompleted(Metrics.of(exploration.metric(), level, Metrics.CURRENT_COST, cost,
                    LOSS, updates == 0 ? 0 : lossSum / updates));
        }
    }

    // the state of an incomplete construction, null when complete
    private Observation observe(FeaturedConstruction<S> construction, int width) {
        return construction.isComplete() ? null : Observation.of(construction, width, rewardBaseline);
    }

    // Q of every action of o: its baseline (known reward) plus the scaled network output
    private static double[] values(Mlp net, Observation o) {
        double[] values = new double[o.size()];
        for (int k = 0; k < values.length; k++)
            values[k] = o.baselines[k] + o.scale * net.forward(o.features, k * o.width);
        return values;
    }

    private static double value(Mlp net, Observation o, int k) {
        return o.baselines[k] + o.scale * net.forward(o.features, k * o.width);
    }

    // index of the highest value; the first one on ties
    static int argmax(double[] values) {
        int best = 0;
        for (int k = 1; k < values.length; k++)
            if (values[k] > values[best])
                best = k;
        return best;
    }

    // indices of the count highest values (all if there are fewer), highest first
    private static int[] top(double[] values, int count) {
        int n = Math.min(count, values.length);
        int[] top = new int[n];
        int size = 0;
        for (int k = 0; k < values.length; k++) {
            if (size == n && values[k] <= values[top[n - 1]])
                continue;
            int i = size < n ? size++ : n - 1;
            while (i > 0 && values[top[i - 1]] < values[k]) {
                top[i] = top[i - 1];
                i--;
            }
            top[i] = k;
        }
        return top;
    }

    // row of the action of o with the highest value among its candidates; the first one on ties
    private static int best(Mlp net, Observation o) {
        int best = -1;
        double bestValue = Double.NEGATIVE_INFINITY;
        int count = o.candidates == null ? o.size() : o.candidates.length;
        for (int i = 0; i < count; i++) {
            int k = o.candidates == null ? i : o.candidates[i];
            double value = value(net, o, k);
            if (best < 0 || value > bestValue) {
                bestValue = value;
                best = k;
            }
        }
        return best;
    }

    // the n-step transitions of an episode: step t sums the discounted rewards of steps t .. t + n - 1
    private List<Transition> transitions(List<float[]> taken, List<Double> baselines, List<Double> scales, List<Double> rewards,
                                         List<Observation> reached) {
        int steps = rewards.size();
        List<Transition> result = new ArrayList<>(steps);
        for (int t = 0; t < steps; t++) {
            double reward = 0, discount = 1;
            int k = 0;
            for (; k < nStep && t + k < steps; k++) {
                reward += discount * rewards.get(t + k);
                discount *= gamma;
            }
            result.add(new Transition(taken.get(t), baselines.get(t), scales.get(t), reward, discount, reached.get(t + k - 1)));
        }
        return result;
    }

    // one Adam step on a batch; returns the mean Huber loss of the batch
    private double train(Mlp online, Mlp target, Adam adam, Mlp.Gradients gradients, ReplayBuffer replay,
                         List<Transition> elite, RNG rng) {
        gradients.clear();
        double loss = 0;
        for (int b = 0; b < batchSize; b++) {
            Transition t = !elite.isEmpty() && rng.rollDice(eliteFraction)
                    ? elite.get(rng.randInt(elite.size())) : replay.sample(rng);
            double y = t.reward;
            if (t.next != null)
                y += t.discount * value(target, t.next, best(online, t.next));
            // the network learns (Q - baseline) / scale, so the error is measured in its units
            double error = online.forward(t.features, 0) - (y - t.baseline) / t.scale;
            double absolute = Math.abs(error);
            loss += absolute <= 1 ? 0.5 * error * error : absolute - 0.5;
            online.backward(t.features, 0, Math.max(-1, Math.min(1, error)), gradients);
        }
        adam.step(online, gradients, 1.0 / batchSize);
        return loss / batchSize;
    }

    /**
     * This algorithm for problems of type P: every run searches the constructive view that the given function builds
     * of the problem. The view must have the same cost function as the problem.
     */
    public <P extends OptimizationProblem<S>> Metaheuristic<S, P> viewing(Function<? super P, ? extends FeaturedConstructiveProblem<S>> view) {
        DQN<S> dqn = this;
        return new Metaheuristic<S, P>() {
            @Override
            public void search(P problem, SearchContext<S> ctx) {
                dqn.search(view.apply(problem), ctx);
            }

            @Override
            public String getName() {
                return dqn.getName();
            }

            @Override
            public String describe() {
                return dqn.describe();
            }

            @Override
            public String toString() {
                return dqn.describe();
            }
        };
    }

    @Override
    protected String parameters() {
        return "hidden=" + Arrays.toString(hidden) + ", lr=" + learningRate + ", gamma=" + gamma + ", nStep=" + nStep
                + ", batch=" + batchSize + ", replay=" + replayCapacity + ", warmup=" + warmup + ", trainEvery=" + trainEvery
                + ", targetUpdate=" + targetUpdate + ", exploration=" + exploration + ", elite=" + eliteFraction
                + ", greedyEvery=" + greedyEvery + ", candidates=" + candidates
                + (initialNetwork == null ? "" : ", initial network=" + Arrays.toString(initialNetwork.layerSizes()))
                + (training ? "" : ", frozen") + (rewardBaseline ? "" : ", no reward baseline");
    }

    public static final class Builder<S extends Representation<S>> {
        private String name = "DQN";
        private int[] hidden = {16, 16};
        private double learningRate = 1e-3;
        private double gamma = 1;
        private int nStep = 3;
        private int batchSize = 32;
        private int replayCapacity = 20000;
        private int warmup = 500;
        private int trainEvery = 1;
        private int targetUpdate = 500;
        private Exploration exploration = Exploration.softmax(0.3, 0.05, 1000);
        private double eliteFraction = 0.25;
        private int greedyEvery = 10;
        private int candidates = 16;
        private Mlp initialNetwork;
        private boolean training = true;
        private Consumer<Mlp> trainedNetwork;
        private boolean rewardBaseline = true;

        private Builder() {
        }

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }

        // sizes of the hidden ReLU layers; default: 16, 16
        public Builder<S> hidden(int... hidden) {
            this.hidden = hidden.clone();
            return this;
        }

        // Adam step size; default: 1e-3
        public Builder<S> learningRate(double learningRate) {
            this.learningRate = learningRate;
            return this;
        }

        // discount factor in [0, 1]; 1 makes the return exactly minus the scaled cost; default: 1
        public Builder<S> gamma(double gamma) {
            this.gamma = gamma;
            return this;
        }

        // steps of real rewards before bootstrapping; default: 3
        public Builder<S> nStep(int nStep) {
            this.nStep = nStep;
            return this;
        }

        // default: 32
        public Builder<S> batchSize(int batchSize) {
            this.batchSize = batchSize;
            return this;
        }

        // transitions kept for replay; default: 20000
        public Builder<S> replayCapacity(int replayCapacity) {
            this.replayCapacity = replayCapacity;
            return this;
        }

        // transitions collected before training starts; default: 500
        public Builder<S> warmup(int warmup) {
            this.warmup = warmup;
            return this;
        }

        // construction steps per training batch; default: 1
        public Builder<S> trainEvery(int trainEvery) {
            this.trainEvery = trainEvery;
            return this;
        }

        // construction steps between target network synchronizations; default: 500
        public Builder<S> targetUpdate(int targetUpdate) {
            this.targetUpdate = targetUpdate;
            return this;
        }

        // default: softmax(0.3 -> 0.05 over 1000 episodes)
        public Builder<S> exploration(Exploration exploration) {
            this.exploration = exploration;
            return this;
        }

        // epsilon-greedy, epsilon decaying linearly from start to end over decayEpisodes
        public Builder<S> epsilon(double start, double end, int decayEpisodes) {
            return exploration(Exploration.epsilonGreedy(start, end, decayEpisodes));
        }

        // softmax over standardized values, the temperature decaying geometrically from start to end over decayEpisodes
        public Builder<S> softmax(double start, double end, int decayEpisodes) {
            return exploration(Exploration.softmax(start, end, decayEpisodes));
        }

        // fraction of every batch drawn from the transitions of the best episode; default: 0.25
        public Builder<S> eliteFraction(double eliteFraction) {
            this.eliteFraction = eliteFraction;
            return this;
        }

        // episodes 0, greedyEvery, 2 greedyEvery, ... are greedy (0 = never); default: 10
        public Builder<S> greedyEvery(int greedyEvery) {
            this.greedyEvery = greedyEvery;
            return this;
        }

        // actions per state among which bootstrap targets maximize (0 = all actions, exact); default: 16
        public Builder<S> candidates(int candidates) {
            this.candidates = candidates;
            return this;
        }

        // runs start from a copy of this network (e.g. trained on another problem), and hidden() is ignored;
        // default: a random network
        public Builder<S> initialNetwork(Mlp network) {
            this.initialNetwork = network;
            return this;
        }

        // false: the network is only used, never trained (no replay, no updates); default: true
        public Builder<S> training(boolean training) {
            this.training = training;
            return this;
        }

        /**
         * Receives a copy of the network of every run when the run ends, also when it is stopped by its budget,
         * e.g. to save it. Called on the run's thread, so it must be thread-safe if runs are concurrent.
         */
        public Builder<S> trainedNetwork(Consumer<Mlp> consumer) {
            this.trainedNetwork = consumer;
            return this;
        }

        /**
         * true: when the problem tells the rewards of the actions in advance (FeaturedConstruction.actionRewards),
         * Q(s, a) = reward(s, a) + network output, and a new network starts at 0, i.e. as the greedy choice of the best
         * reward; the network learns only the value of what follows an action. false: the network learns all of Q.
         * Default: true
         */
        public Builder<S> rewardBaseline(boolean rewardBaseline) {
            this.rewardBaseline = rewardBaseline;
            return this;
        }

        public DQN<S> build() {
            return new DQN<>(this);
        }
    }
}
