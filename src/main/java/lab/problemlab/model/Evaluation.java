package lab.problemlab.model;

import base.OptimizationProblem;
import representation.base.Representation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Problem Lab shows about a solution: its cost, whether it is feasible, which constraints it violates (in
 * words) and any problem-specific measures in display order, e.g. the number of trips.
 */
public record Evaluation(double cost, boolean feasible, List<String> violations, Map<String, String> metrics) {

    public Evaluation {
        violations = List.copyOf(violations);
        metrics = Collections.unmodifiableMap(new LinkedHashMap<>(metrics));   // keeps the order
    }

    // just the cost and the feasibility, as the problem computes them
    public static <S extends Representation<S>> Evaluation of(OptimizationProblem<S> problem, S solution) {
        return builder(problem.cost(solution), problem.isFeasible(solution)).build();
    }

    public static Builder builder(double cost, boolean feasible) {
        return new Builder(cost, feasible);
    }

    // feasible solutions first, then lower cost
    public boolean isBetterThan(Evaluation other) {
        return feasible != other.feasible ? feasible : cost < other.cost;
    }

    public static final class Builder {
        private final double cost;
        private final boolean feasible;
        private final List<String> violations = new ArrayList<>();
        private final Map<String, String> metrics = new LinkedHashMap<>();

        private Builder(double cost, boolean feasible) {
            this.cost = cost;
            this.feasible = feasible;
        }

        public Builder violation(String description) {
            violations.add(description);
            return this;
        }

        public Builder metric(String name, Object value) {
            metrics.put(name, String.valueOf(value));
            return this;
        }

        public Evaluation build() {
            return new Evaluation(cost, feasible, violations, metrics);
        }
    }
}
