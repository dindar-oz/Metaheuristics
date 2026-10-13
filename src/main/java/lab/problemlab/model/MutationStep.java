package lab.problemlab.model;

import base.MutationOperator;
import base.OptimizationProblem;
import representation.base.Representation;
import util.random.RNG;

/**
 * The outcome of applying a mutation operator a number of times, each time to the solution accepted so far, keeping
 * neighbors by an {@link Acceptance} rule. With {@link Acceptance#ALWAYS} this is a random walk, with the others a
 * stochastic hill climber: a way to watch what an operator does to a solution.
 *
 * @param tried    the number of times the operator was applied
 * @param changed  the number of neighbors that differed from the solution they were made from
 * @param accepted the number of neighbors that were kept
 */
public record MutationStep<S>(S solution, int tried, int changed, int accepted) {

    public enum Acceptance {
        ALWAYS("always"),
        NOT_WORSE("if not worse"),
        BETTER("if better");

        private final String label;

        Acceptance(String label) {
            this.label = label;
        }

        // feasible solutions are better than infeasible ones, then lower costs are better
        boolean accepts(boolean feasible, double cost, boolean nextFeasible, double nextCost) {
            return switch (this) {
                case ALWAYS -> true;
                case NOT_WORSE -> nextFeasible != feasible ? nextFeasible : nextCost <= cost;
                case BETTER -> nextFeasible != feasible ? nextFeasible : nextCost < cost;
            };
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static <S extends Representation<S>> MutationStep<S> run(OptimizationProblem<S> problem, S start,
                                                                    MutationOperator<S> operator, int times,
                                                                    Acceptance acceptance, RNG rng) {
        S current = start;
        double cost = problem.cost(current);
        boolean feasible = problem.isFeasible(current);
        int accepted = 0, changed = 0;
        for (int k = 0; k < times; k++) {
            S next = operator.apply(current, rng);
            if (next.equals(current))
                continue;               // the operator found nothing to change
            changed++;
            double nextCost = problem.cost(next);
            boolean nextFeasible = problem.isFeasible(next);
            if (acceptance.accepts(feasible, cost, nextFeasible, nextCost)) {
                current = next;
                cost = nextCost;
                feasible = nextFeasible;
                accepted++;
            }
        }
        return new MutationStep<>(current, times, changed, accepted);
    }
}
