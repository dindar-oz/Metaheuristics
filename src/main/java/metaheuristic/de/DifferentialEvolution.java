package metaheuristic.de;

import base.ContinuousProblem;
import base.InitialSolutionGenerator;
import base.OptimizationProblem;
import base.TerminalCondition;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.BasePIterationEvent;
import representation.AgeingIndividual;
import representation.DoubleVector;
import representation.ListPopulation;
import representation.base.Individual;
import representation.base.Population;
import representation.base.Representation;
import util.random.RandUtil;

/**
 * Classic DE/rand/1/bin. Mutants leaving the search box are corrected by the BoundaryHandler.
 */
public class DifferentialEvolution extends AbstractMetaheuristic {

    private final BoundaryHandler boundaryHandler;
    private int populationSize = 50;
    private double crossoverRate = 0.9;
    private double differentialWeight = 0.8;

    public DifferentialEvolution(BoundaryHandler boundaryHandler, TerminalCondition tc) {
        super(tc);
        this.boundaryHandler = boundaryHandler;
    }

    public DifferentialEvolution(BoundaryHandler boundaryHandler, TerminalCondition tc,
                                 int populationSize, double crossoverRate, double differentialWeight) {
        this(boundaryHandler, tc);
        if (populationSize < 4)
            throw new IllegalArgumentException("DE needs a population of at least 4");
        this.populationSize = populationSize;
        this.crossoverRate = crossoverRate;
        this.differentialWeight = differentialWeight;
    }

    @Override
    protected void run(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator) {
        if (!(problem instanceof ContinuousProblem))
            throw new IllegalArgumentException("DifferentialEvolution requires a ContinuousProblem.");
        ContinuousProblem cProblem = (ContinuousProblem) problem;

        Population population = new ListPopulation();
        for (Representation r : solutionGenerator.generate(problem, populationSize))
            population.add(new AgeingIndividual(r, evaluate(problem, r)));

        while (!isTerminated()) {
            Population newPopulation = new ListPopulation();

            for (int i = 0; i < populationSize; i++) {
                Individual target = population.get(i);
                double[] mutant = mutate(population, i);
                boundaryHandler.handle(cProblem, mutant, ((DoubleVector) target.getRepresentation()).getValues());

                DoubleVector trial = crossOver(mutant, (DoubleVector) target.getRepresentation());
                double trialCost = evaluate(problem, trial);

                newPopulation.add(trialCost < target.getCost() ? new AgeingIndividual(trial, trialCost) : target);
            }

            population = newPopulation;
            iterationCount++;

            if (hasListeners()) {
                Individual best = population.getBest();
                fireIterationEvent(new BasePIterationEvent(iterationCount, getNeighboringCount(),
                        best.getCost(), best.getRepresentation(), population));
            }
        }
    }

    // v = a + F * (b - c) for three distinct random individuals other than index
    private double[] mutate(Population population, int index) {
        int a, b, c;
        do { a = RandUtil.randInt(populationSize); } while (a == index);
        do { b = RandUtil.randInt(populationSize); } while (b == index || b == a);
        do { c = RandUtil.randInt(populationSize); } while (c == index || c == a || c == b);

        double[] A = ((DoubleVector) population.get(a).getRepresentation()).getValues();
        double[] B = ((DoubleVector) population.get(b).getRepresentation()).getValues();
        double[] C = ((DoubleVector) population.get(c).getRepresentation()).getValues();

        double[] mutant = new double[A.length];
        for (int d = 0; d < A.length; d++)
            mutant[d] = A[d] + differentialWeight * (B[d] - C[d]);
        return mutant;
    }

    // Binomial crossover; at least one dimension always comes from the mutant
    private DoubleVector crossOver(double[] mutant, DoubleVector target) {
        double[] trial = new double[mutant.length];
        int randIndex = RandUtil.randInt(mutant.length);
        for (int d = 0; d < mutant.length; d++)
            trial[d] = (d == randIndex || RandUtil.rollDice(crossoverRate)) ? mutant[d] : target.get(d);
        return new DoubleVector(trial);
    }

    @Override
    protected String defaultName() {
        return "DE";
    }
}
