package metaheuristic.island;

import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Emigration;
import metaheuristic.Immigration;
import metaheuristic.Params;
import metaheuristic.SearchProcess;
import metaheuristic.SteppableMetaheuristic;
import metaheuristic.run.SearchContext;
import representation.base.Individual;
import representation.base.Representation;
import util.random.MersenneTwisterRNG;
import util.random.Seeds;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An island model: several runs of {@link SteppableMetaheuristic}s (the islands) search the same problem and exchange
 * solutions through directed channels. It runs in rounds. In a round every island makes its {@code steps} iterations
 * on its own (e.g. one ant colony iteration, or 35 generations of a GA, so that the islands get comparable time).
 * Then, at the round's end, every channel asks its {@link MigrationTrigger}; if it fires, the {@link MigrantSelector}
 * picks among the solutions the source offers ({@link Emigration}) and the target takes them in
 * ({@link Immigration}), already evaluated, as copies. So the islands interact only between rounds, never within one.
 * <p>
 * Without channels it is a portfolio: the islands run side by side and the run keeps the best solution of all. One
 * channel gives one-way migration, a ring of channels between copies of one algorithm the classic island model.
 * <p>
 * Every evaluation of every island counts for the run and its stop condition; each island has its own RNG, derived
 * from the run's, its own counters and best (see IslandContext). The islands run one after the other on the run's
 * thread, so a run is the same for the same seed. Each round is one iteration of the run, with these metrics for
 * every island {@code name}: {@code name.bestCost}, {@code name.iterations}, {@code name.evaluations},
 * {@code name.millis} (time spent stepping it), {@code name.diversity} and {@code name.duplicates} (of its population
 * in its last iteration of the round, if it reports one) and its own last metrics as {@code name.<metric>}; and for
 * every channel {@code from>to.migrations} and {@code from>to.migrants}.
 */
public final class IslandModel<S extends Representation<S>, P extends OptimizationProblem<S>>
        extends AbstractMetaheuristic<S, P> {

    private record Island<S extends Representation<S>, P extends OptimizationProblem<S>>(
            String name, SteppableMetaheuristic<S, ? super P> algorithm, int steps) {
        @Override
        public String toString() {
            return name + "=" + algorithm.describe() + " x" + steps;
        }
    }

    private record Channel(int from, int to, String name, MigrationTrigger trigger, MigrantSelector selector) {
        @Override
        public String toString() {
            return name + " when " + trigger + ", " + selector;
        }
    }

    private final List<Island<S, P>> islands;
    private final List<Channel> channels;

    private IslandModel(Builder<S, P> b) {
        super(b.name);
        islands = Params.nonEmpty("islands", b.islands);
        channels = Params.immutableCopy(b.channels);
    }

    public static <S extends Representation<S>, P extends OptimizationProblem<S>> Builder<S, P> builder() {
        return new Builder<>();
    }

    @Override
    public void search(P problem, SearchContext<S> ctx) {
        new Run(ctx).search(problem);
    }

    @Override
    protected String parameters() {
        return "islands=" + Params.describe(islands) + ", channels=" + Params.describe(channels);
    }

    // the state of one run
    private final class Run {
        private final SearchContext<S> ctx;
        private final List<IslandContext<S>> contexts = new ArrayList<>();
        private final List<SearchProcess> processes = new ArrayList<>();
        private final long[] nanos = new long[islands.size()];
        private final long[] lastMigration = new long[channels.size()];    // the target's iterations at it
        private final int[] migrations = new int[channels.size()], migrants = new int[channels.size()];

        Run(SearchContext<S> ctx) {
            this.ctx = ctx;
            long base = Seeds.derive(ctx.rng().randInt(Integer.MAX_VALUE), ctx.rng().randInt(Integer.MAX_VALUE));
            for (int i = 0; i < islands.size(); i++)
                contexts.add(new IslandContext<>(ctx, new MersenneTwisterRNG(Seeds.derive(base, i))));
        }

        void search(P problem) {
            try {
                for (int i = 0; i < islands.size() && !ctx.shouldStop(); i++) {
                    long start = System.nanoTime();
                    processes.add(islands.get(i).algorithm().start(problem, contexts.get(i)));
                    nanos[i] += System.nanoTime() - start;
                }
                if (ctx.shouldStop())
                    return;
                checkCapabilities();
                while (!ctx.shouldStop() && !allFinished()) {
                    for (int i = 0; i < islands.size(); i++)
                        stepIsland(i);
                    if (ctx.shouldStop())
                        return;
                    for (int c = 0; c < channels.size(); c++)
                        migrate(c);
                    ctx.iterationCompleted(metrics());
                }
            } finally {
                for (SearchProcess p : processes)
                    p.close();
            }
        }

        private void checkCapabilities() {
            for (Channel c : channels) {
                if (!(processes.get(c.from()) instanceof Emigration))
                    throw new IllegalStateException(islands.get(c.from()).algorithm().getName()
                            + " cannot send migrants (channel " + c.name() + ")");
                if (!(processes.get(c.to()) instanceof Immigration))
                    throw new IllegalStateException(islands.get(c.to()).algorithm().getName()
                            + " cannot take in migrants (channel " + c.name() + ")");
            }
        }

        private boolean allFinished() {
            for (SearchProcess p : processes)
                if (!p.finished())
                    return false;
            return true;
        }

        // the island's steps of the round; its population is measured in the last one
        private void stepIsland(int i) {
            SearchProcess process = processes.get(i);
            IslandContext<S> context = contexts.get(i);
            int steps = islands.get(i).steps();
            long start = System.nanoTime();
            try {
                for (int s = 0; s < steps && !ctx.shouldStop() && !process.finished(); s++) {
                    context.observe(s == steps - 1);
                    process.step();
                }
            } finally {
                context.observe(false);
                nanos[i] += System.nanoTime() - start;
            }
        }

        @SuppressWarnings("unchecked")
        private void migrate(int c) {
            Channel channel = channels.get(c);
            IslandContext<S> source = contexts.get(channel.from()), target = contexts.get(channel.to());
            if (!channel.trigger().fires(source, target, target.iterations() - lastMigration[c]))
                return;
            Immigration<S> receiver = (Immigration<S>) processes.get(channel.to());
            List<Individual<S>> chosen = channel.selector().select(
                    ((Emigration<S>) processes.get(channel.from())).emigrants(), receiver.residents());
            if (chosen.isEmpty())
                return;
            List<Individual<S>> copies = new ArrayList<>(chosen.size());
            for (Individual<S> m : chosen)
                copies.add(new Individual<>(m.getRepresentation().copy(), m.getCost()));
            receiver.immigrate(copies);
            target.received(copies);
            lastMigration[c] = target.iterations();
            migrations[c]++;
            migrants[c] += copies.size();
        }

        private Map<String, Double> metrics() {
            Map<String, Double> m = new LinkedHashMap<>();
            for (int i = 0; i < islands.size(); i++) {
                String name = islands.get(i).name() + ".";
                IslandContext<S> context = contexts.get(i);
                m.put(name + "bestCost", context.bestCost());
                m.put(name + "iterations", (double) context.iterations());
                m.put(name + "evaluations", (double) context.evaluations());
                m.put(name + "millis", nanos[i] / 1e6);
                if (!Double.isNaN(context.diversity()))
                    m.put(name + "diversity", context.diversity());
                if (!Double.isNaN(context.duplicates()))
                    m.put(name + "duplicates", context.duplicates());
                context.metrics().forEach((k, v) -> m.put(name + k, v));
            }
            for (int c = 0; c < channels.size(); c++) {
                m.put(channels.get(c).name() + ".migrations", (double) migrations[c]);
                m.put(channels.get(c).name() + ".migrants", (double) migrants[c]);
            }
            return m;
        }
    }

    public static final class Builder<S extends Representation<S>, P extends OptimizationProblem<S>> {
        private String name = "Island model";
        private final List<Island<S, P>> islands = new ArrayList<>();
        private final List<Channel> channels = new ArrayList<>();

        private Builder() {
        }

        public Builder<S, P> name(String name) {
            this.name = name;
            return this;
        }

        /**
         * Adds an island: a run of the algorithm that makes `steps` iterations per round. The name (letters, digits,
         * '-' and '_') prefixes its metrics and identifies it in channels.
         */
        public Builder<S, P> island(String name, SteppableMetaheuristic<S, ? super P> algorithm, int steps) {
            if (name == null || !name.matches("[A-Za-z0-9_-]+"))
                throw new IllegalArgumentException("island names are letters, digits, '-' and '_': " + name);
            if (indexOf(name) >= 0)
                throw new IllegalArgumentException("two islands are named " + name);
            islands.add(new Island<>(name, Params.required("algorithm", algorithm), Params.positive("steps", steps)));
            return this;
        }

        /**
         * Adds a channel from one island to another; the source's process must offer solutions ({@link Emigration})
         * and the target's take them in ({@link Immigration}). Channels are asked in the order they were added.
         */
        public Builder<S, P> channel(String from, String to, MigrationTrigger trigger, MigrantSelector selector) {
            int f = existing(from), t = existing(to);
            if (f == t)
                throw new IllegalArgumentException("a channel needs two islands: " + from);
            channels.add(new Channel(f, t, from + ">" + to, Params.required("trigger", trigger),
                    Params.required("selector", selector)));
            return this;
        }

        private int existing(String island) {
            int i = indexOf(island);
            if (i < 0)
                throw new IllegalArgumentException("no island is named " + island);
            return i;
        }

        private int indexOf(String island) {
            for (int i = 0; i < islands.size(); i++)
                if (islands.get(i).name().equals(island))
                    return i;
            return -1;
        }

        public IslandModel<S, P> build() {
            return new IslandModel<>(this);
        }
    }
}
