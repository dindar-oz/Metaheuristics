package metaheuristic.ea;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import base.TerminalCondition;
import metaheuristic.AbstractMetaheuristic;
import metaheuristic.BasePIterationEvent;
import metaheuristic.ea.base.*;
import metaheuristic.ea.crossover.SimpleCrossOverStrategy;
import metaheuristic.ea.victimselector.SimpleVictimSelector;
import representation.AgeingIndividual;
import representation.CostBasedComparator;
import representation.ListPopulation;
import representation.base.Individual;
import representation.base.Population;
import representation.base.Representation;

import java.util.Collections;
import java.util.List;

/**
 * Steady-state evolutionary algorithm with optional immigration, ageing and local search.
 */
public class EA extends AbstractMetaheuristic {

    private final List<CrossOverOperator> crossOverOperators;
    private final List<MutationOperator> mutationOperators;
    private final ParentSelector parentSelector;

    private CrossOverStrategy crossOverStrategy = new SimpleCrossOverStrategy(0.8);
    private MutationStrategy mutationStrategy = new SimpleMutationStrategy(0.8, 1);
    private VictimSelector victimSelector = new SimpleVictimSelector();

    private int populationSize = 50;

    private int immigrationPeriod = 0; // 0 means no immigration
    private int immigrantCount = 20;

    private int ageingThreshold = 0; // 0 means no ageing

    private MutationOperator localSearch = null;
    private int localSearchCount = 0;

    public EA(List<CrossOverOperator> crossOverOperators, List<MutationOperator> mutationOperators,
              ParentSelector parentSelector, TerminalCondition terminalCondition) {
        super(terminalCondition);
        this.crossOverOperators = crossOverOperators == null ? Collections.emptyList() : crossOverOperators;
        this.mutationOperators = mutationOperators == null ? Collections.emptyList() : mutationOperators;
        this.parentSelector = parentSelector;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public void setCrossOverStrategy(CrossOverStrategy crossOverStrategy) {
        this.crossOverStrategy = crossOverStrategy;
    }

    public void setMutationStrategy(MutationStrategy mutationStrategy) {
        this.mutationStrategy = mutationStrategy;
    }

    public void setVictimSelector(VictimSelector victimSelector) {
        this.victimSelector = victimSelector;
    }

    public void setImmigrationPeriod(int immigrationPeriod) {
        this.immigrationPeriod = immigrationPeriod;
    }

    public void setImmigrantCount(int immigrantCount) {
        this.immigrantCount = immigrantCount;
    }

    public void setAgeingThreshold(int ageingThreshold) {
        this.ageingThreshold = ageingThreshold;
    }

    // Each iteration, the given operator is applied to the best localSearchCount individuals
    public void setLocalSearch(MutationOperator localSearch, int localSearchCount) {
        this.localSearch = localSearch;
        this.localSearchCount = localSearchCount;
    }

    @Override
    protected void run(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator) {
        Population population = generatePopulation(problem, solutionGenerator, populationSize);

        while (!isTerminated()) {
            applyCrossOver(problem, population);
            mutationStrategy.applyMutations(problem, population, mutationOperators, r -> evaluate(problem, r));
            iterationCount++;

            if (immigrationPeriod > 0 && iterationCount % immigrationPeriod == 0)
                acceptImmigrants(problem, population, solutionGenerator);

            if (ageingThreshold > 0)
                performAgeing(problem, solutionGenerator, population);

            if (localSearch != null && localSearchCount > 0)
                applyLocalSearch(problem, population);

            trimPopulation(problem, population);

            if (hasListeners()) {
                Individual best = population.getBest();
                fireIterationEvent(new BasePIterationEvent(iterationCount, getNeighboringCount(),
                        best.getCost(), best.getRepresentation(), population));
            }
        }
    }

    private Population generatePopulation(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator, int count) {
        Population population = new ListPopulation();
        for (Representation r : solutionGenerator.generate(problem, count))
            population.add(new AgeingIndividual(r, evaluate(problem, r)));
        return population;
    }

    private void applyCrossOver(OptimizationProblem problem, Population population) {
        if (crossOverOperators.isEmpty())
            return;

        List<Individual> parents = parentSelector.selectParents(problem, population.getIndividuals());
        List<Individual> offsprings = crossOverStrategy.generateOffsprings(problem, parents, crossOverOperators,
                r -> evaluate(problem, r));
        List<Individual> victims = victimSelector.selectVictims(problem, population.getIndividuals(), offsprings.size());

        for (Individual offspring : offsprings) {
            if (!population.contains(offspring)) {
                population.remove(victims.remove(0));
                population.add(new AgeingIndividual(offspring));
            }
        }
    }

    // Removes the worst individuals if the population grew beyond its size
    private void trimPopulation(OptimizationProblem problem, Population population) {
        int victimCount = population.size() - populationSize;
        if (victimCount <= 0)
            return;

        List<Individual> victims = victimSelector.selectVictims(problem, population.getIndividuals(), victimCount);
        population.removeAll(victims);
    }

    private void applyLocalSearch(OptimizationProblem problem, Population population) {
        population.sort(new CostBasedComparator());

        int count = Math.min(localSearchCount, population.size());
        for (int i = 0; i < count; i++) {
            Representation r = localSearch.apply(problem, population.get(i).getRepresentation());
            population.add(new AgeingIndividual(r, evaluate(problem, r)));
        }
    }

    private void performAgeing(OptimizationProblem problem, InitialSolutionGenerator solutionGenerator, Population population) {
        population.forEach(Individual::age);
        population.removeIf(i -> i.getAge() > ageingThreshold);
        int removed = populationSize - population.size();
        if (removed > 0)
            population.add(generatePopulation(problem, solutionGenerator, removed));
    }

    // Replaces the worst immigrantCount individuals with newly generated ones
    private void acceptImmigrants(OptimizationProblem problem, Population population, InitialSolutionGenerator solutionGenerator) {
        population.sort(new CostBasedComparator());
        int count = Math.min(immigrantCount, population.size());
        for (int i = 0; i < count; i++)
            population.remove(population.size() - 1);
        population.add(generatePopulation(problem, solutionGenerator, count));
    }

    @Override
    protected String defaultName() {
        return "EA";
    }
}
