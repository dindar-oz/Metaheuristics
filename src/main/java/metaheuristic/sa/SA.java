package metaheuristic.sa;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.Params;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import representation.base.Representation;
import util.random.RNG;

import java.util.List;

/**
 * Simulated annealing. At each temperature level innerLoopLength neighbors are tried,
 * then the temperature is lowered with the cooling schedule.
 * Finishes by itself when the temperature drops below the minimum.
 * <pre>
 * SA sa = SA.builder(ops, isg).cooling(new GeometricCooling(0.995)).initialTemperature(1).build();
 * </pre>
 */
public final class SA<S extends Representation<S>> extends AbstractMetaheuristic<S, OptimizationProblem<S>> {

    public static final String TEMPERATURE = "temperature";

    private final List<MutationOperator<S>> operators;
    private final InitialSolutionGenerator<S> generator;
    private final CoolingSchedule coolingSchedule;
    private final double initialTemperature;
    private final double minimumTemperature;
    private final int innerLoopLength;

    private SA(Builder<S> b) {
        super(b.name);
        this.operators = Params.nonEmpty("operators", b.operators);
        this.generator = Params.required("generator", b.generator);
        this.coolingSchedule = Params.required("cooling", b.cooling);
        this.initialTemperature = Params.positive("initialTemperature", b.initialTemperature);
        this.minimumTemperature = Params.nonNegative("minimumTemperature", b.minimumTemperature);
        this.innerLoopLength = Params.positive("innerLoopLength", b.innerLoopLength);
        if (minimumTemperature >= initialTemperature)
            throw new IllegalArgumentException("minimumTemperature must be below initialTemperature");
    }

    public static <S extends Representation<S>> Builder<S> builder(List<MutationOperator<S>> operators, InitialSolutionGenerator<S> generator) {
        Builder<S> b = new Builder<>();
        b.operators = operators;
        b.generator = generator;
        return b;
    }

    @Override
    public void search(OptimizationProblem<S> problem, SearchContext<S> ctx) {
        RNG rng = ctx.rng();
        S current = generator.generate(problem, rng);
        double currentCost = ctx.evaluate(current);
        double temperature = initialTemperature;

        while (temperature > minimumTemperature && !ctx.shouldStop()) {
            for (int i = 0; i < innerLoopLength && !ctx.shouldStop(); i++) {
                MutationOperator<S> nf = operators.get(rng.randInt(operators.size()));
                S neighbor = nf.apply(current, rng);
                double neighborCost = ctx.evaluate(neighbor);

                if (neighborCost < currentCost || rng.rollDice(Math.exp((currentCost - neighborCost) / temperature))) {
                    current = neighbor;
                    currentCost = neighborCost;
                }
                ctx.iterationCompleted(Metrics.of(TEMPERATURE, temperature, Metrics.CURRENT_COST, currentCost));
            }
            temperature = coolingSchedule.updateTemperature(temperature);
        }
    }

    @Override
    protected String parameters() {
        return "operators=" + Params.describe(operators) + ", cooling=" + coolingSchedule
                + ", t0=" + initialTemperature + ", tMin=" + minimumTemperature + ", innerLoop=" + innerLoopLength;
    }

    public static final class Builder<S extends Representation<S>> {
        private String name = "SA";
        private List<MutationOperator<S>> operators;
        private InitialSolutionGenerator<S> generator;
        private CoolingSchedule cooling = new GeometricCooling(0.99);
        private double initialTemperature = 1.0;
        private double minimumTemperature = 1e-6;
        private int innerLoopLength = 5;

        private Builder() {
        }

        public Builder<S> name(String name) {
            this.name = name;
            return this;
        }



        // default: geometric(0.99)
        public Builder<S> cooling(CoolingSchedule cooling) {
            this.cooling = cooling;
            return this;
        }

        // default: 1.0
        public Builder<S> initialTemperature(double initialTemperature) {
            this.initialTemperature = initialTemperature;
            return this;
        }

        // default: 1e-6
        public Builder<S> minimumTemperature(double minimumTemperature) {
            this.minimumTemperature = minimumTemperature;
            return this;
        }

        // neighbors tried per temperature level; default: 5
        public Builder<S> innerLoopLength(int innerLoopLength) {
            this.innerLoopLength = innerLoopLength;
            return this;
        }

        public SA<S> build() {
            return new SA<>(this);
        }
    }
}
