package metaheuristic.island;

import metaheuristic.Params;
import metaheuristic.run.Progress;

/**
 * When a channel of an {@link IslandModel} sends migrants: asked at the end of every round, with the progress of the
 * two islands as each sees it. An island's best cost includes the migrants it received, so a trigger that compares
 * best costs fires once per improvement, not in every round. The triggers are stateless; what they need to remember
 * (the last migration) is passed in.
 */
@FunctionalInterface
public interface MigrationTrigger {

    /**
     * @param source                          the progress of the island that would send
     * @param target                          the progress of the island that would receive
     * @param targetIterationsSinceMigration  iterations of the target since this channel last delivered migrants, or
     *                                        since the start if it never did
     */
    boolean fires(Progress source, Progress target, long targetIterationsSinceMigration);

    // fires when either trigger fires
    default MigrationTrigger or(MigrationTrigger other) {
        return new Or(this, other);
    }

    static MigrationTrigger never() {
        return new Never();
    }

    // every `iterations` iterations of the target (the classic island model's migration interval)
    static MigrationTrigger every(int iterations) {
        return new Every(Params.positive("iterations", iterations));
    }

    /**
     * When the target is stuck: its best has not improved for `iterations` iterations, and the channel has not
     * delivered for as long, so a migration that does not help is not repeated at once.
     */
    static MigrationTrigger stagnation(int iterations) {
        return new Stagnation(Params.positive("iterations", iterations));
    }

    // when the source's best is better than the target's by more than the fraction `margin` of the target's, in [0, 1)
    static MigrationTrigger whenBetter(double margin) {
        return new WhenBetter(Params.below("margin", Params.nonNegative("margin", margin), 1));
    }

    record Never() implements MigrationTrigger {
        @Override
        public boolean fires(Progress source, Progress target, long sinceMigration) {
            return false;
        }
    }

    record Every(int iterations) implements MigrationTrigger {
        @Override
        public boolean fires(Progress source, Progress target, long sinceMigration) {
            return sinceMigration >= iterations;
        }
    }

    record Stagnation(int iterations) implements MigrationTrigger {
        @Override
        public boolean fires(Progress source, Progress target, long sinceMigration) {
            return Math.min(target.iterationsSinceImprovement(), sinceMigration) >= iterations;
        }
    }

    record WhenBetter(double margin) implements MigrationTrigger {
        @Override
        public boolean fires(Progress source, Progress target, long sinceMigration) {
            double theirs = target.bestCost();
            return Double.isInfinite(theirs) ? source.bestCost() < theirs
                    : source.bestCost() < theirs - margin * Math.abs(theirs);
        }
    }

    record Or(MigrationTrigger first, MigrationTrigger second) implements MigrationTrigger {
        @Override
        public boolean fires(Progress source, Progress target, long sinceMigration) {
            return first.fires(source, target, sinceMigration) || second.fires(source, target, sinceMigration);
        }
    }
}
