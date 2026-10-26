package alg.ga.pecto;

import alg.*;
import alg.ga.*;
import alg.greedy.pecto.PECTOGreedyHeuristic;
import alg.greedy.pecto.PECTONearestParticipantEvaluator;
import alg.ls.pecto.PECTOLocalSearch;
import alg.sa.GeometricUpdate;
import alg.sa.SimulatedAnnealing;
import alg.sa.pecto.PECTORandomSwapNG;
import problem.OptimizationProblem;
import problem.ProblemModel;
import problem.SimpleOptimizationProblem;
import problem.pecto.*;
import representation.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static utils.RandUtils.*;

public class PECTOGA<PM extends ProblemModel<R>, R extends Representation> extends BaseMetaheuristic<PM,R> {
    InitialSolutionGenerator<PM,R> isg;
    int populationSize;
    List<Solution> population;
    ParentSelector parentSelector;
    VictimSelector victimSelector;
    protected double crossOverRate;
    protected CrossOverOperator crossOverOperator1 = new PECTOCrossOverARX();
    private final CrossOverOperator crossOverOperator2 = new PECTOCrossOverOX();
    private final ParentSelector parentSelector1 = new RouletteWheelSelection();
    private final ParentSelector parentSelector2 = new TournamentSelection();
    private double mutationRate;
    private double initialMutationRate;
    private final MutationOperation swapMutation1;
    private final MutationOperation swapMutation2;
    private MutationOperation moveMutation;
    private final MutationOperation stagnationMove;
    protected final PECTOGreedyHeuristic greedyHeuristic;
    protected final boolean hybrid;
    private SimulatedAnnealing<PECTOModel2, IntegerPermutation> sa;
    private PECTOLocalSearch ls;
    private PECTOLocalSearch lahc;
    private int stagnationCount = 0;
    private double previousPopBest;
    private int migrantTurn;
    private int stagnationPeriod = 100;

    public PECTOGA(TerminalCondition<PM, R> terminalCondition, boolean hybrid) {
        super(terminalCondition);
        this.hybrid = hybrid;
        this.swapMutation1 = new PECTOMutationSwap();
        this.swapMutation2 = new PECTOMutationSwap2();
        stagnationMove = new PECTOStagnationMove();
        greedyHeuristic = new PECTOGreedyHeuristic(new PECTONearestParticipantEvaluator(),
                (TerminalCondition<PECTOModel2, IntegerPermutation>) terminalCondition);
    }

    public void step(OptimizationProblem<PM, R> problem) {
        List<Solution> parents = selectParents(problem);
        List<Solution> offspring = produceOffspring(problem,parents);

        if (checkStagnation()) {
            for (int i = 0; i < (populationSize * 0.1); i++) {
                switch (migrantTurn) {
                    case 0:
                        insertRandomSolution(problem);
                        break;
                    case 1:
                        insertHybridSolution(problem);
                        break;
                    case 2:
                        insertGreedySolution(problem);
                        break;
                }
            }

            migrantTurn = (migrantTurn + 1) % 3;
            adaptMutationRate(1.1);
            stagnationCount = 0;
            sortPopulationByAge();

            for (int i = (int)(populationSize * 0.1); i < populationSize; i++) {
                swapMutation1.apply(problem, population.get(i));
                updateBest(problem, population.get(i));
            }

        } else {
            applyMutation(problem, offspring);
        }

        refineOffspring(problem, offspring);

        if (hybrid) {
            population.sort(Comparator.comparingDouble(Solution::value));
            Solution best = population.getFirst();
            sa.reset(best);
            Solution refined = sa._perform(problem);
            population.set(0, refined);
            updateBest(problem, refined);
        }

        replacePopulation(problem,offspring);
        incrementAge();
        iterationCount++;
    }

    protected void refineOffspring(OptimizationProblem<PM, R> problem, List<Solution> offspring) {
        if (hybrid && !offspring.isEmpty()) {
            for (int i = 0; i < offspring.size(); i++) {
                ls.reset(offspring.get(i));
                Solution refined = ls._perform(problem);
                offspring.set(i, refined);
                updateBest(problem, refined);
            }
        }
    }

    @Override
    protected Solution _perform(OptimizationProblem<PM, R> problem) {
        System.out.println("NEW ITERATION");
        while (!terminalCondition.isSatisfied(this,problem))
        {
            step(problem);
        }
        return bestSolution;
    }

    private boolean checkStagnation() {
        double currentBest = population.stream()
                .mapToDouble(Solution::value)
                .min()
                .orElse(Double.MAX_VALUE);

        if (currentBest < previousPopBest) {
            stagnationCount = 0;
            previousPopBest = currentBest;
            System.out.println("YES " + currentBest + " IT: " + getIterationCount());
            return false;
        } else {
            stagnationCount++;
            return stagnationCount >= stagnationPeriod;
        }
    }

    void incrementAge() {
        for (Solution solution : population) {
            ((PectoSolution) solution).incrementAge();
        }
    }

    public void setMoveMutation(MutationOperation moveMutation) {
        this.moveMutation = moveMutation;
    }

    void replacePopulation(OptimizationProblem<PM, R> problem, List<Solution> offspring) {
//        List<Solution> victims = victimSelector.select(problem, population, offspring.size());
//        population.removeAll(victims);
//        population.addAll(offspring);

        List<Solution> victims = victimSelector.select(problem, population, offspring.size());
        for (int i = 0; i < victims.size() && i < offspring.size(); i++) {
            int idx = population.indexOf(victims.get(i));
            if (idx != -1) population.set(idx, offspring.get(i));
        }
    }

    void applyMutation(OptimizationProblem<PM, R> problem, List<Solution> offspring) {
        for (Solution s : offspring) {
//            moveMutation.apply(problem, s);
//            updateBest(problem, s);

            if (rollDice(mutationRate)) {
                stagnationMove.apply(problem, s);

//                if (rollDice(0.75)) {
//                    stagnationMove.apply(problem, s);
//                } else {
//                    swapMutation2.apply(problem, s);
//                }

                updateBest(problem, s);
            }
        }
    }

    List<Solution> selectParents(OptimizationProblem<PM, R> problem) {
        return rollDice(0.75)
                ? parentSelector1.select(problem, population)
                : parentSelector2.select(problem, population);
    }

//    List<Solution> produceOffspring(OptimizationProblem<PM, R> problem, List<Solution> parents) {
//        List<int[]> pairs = new ArrayList<>();
//        int size = parents.size();
//
//        for (int p1 = 0; p1 < size; p1++) {
//            for (int p2 = p1 + 1; p2 < size; p2++) {
//                pairs.add(new int[]{p1, p2});
//            }
//        }
//
//        List<Solution> offSpring = pairs.parallelStream()
//                .map(pair -> {
//                    int p1 = pair[0];
//                    int p2 = pair[1];
//
//                    List<Solution> localChildren = new ArrayList<>();
//
//                    if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() < crossOverRate) {
//
//                        Solution parent1 = parents.get(p1);
//                        Solution parent2 = parents.get(p2);
//
//                        List<Solution> children = crossOverOperator1.apply(problem, parent1, parent2);
//
//                        if (children != null && !children.isEmpty()) {
//                            localChildren.addAll(children);
//                        }
//                    }
//
//                    return localChildren;
//                })
//                .flatMap(List::stream)
//                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
//
//        for (Solution s : offSpring) {
//            updateBest(problem, s);
//        }
//
//        return offSpring;
//    }

    List<Solution> produceOffspring(OptimizationProblem<PM, R> problem, List<Solution> parents) {
        List<Solution> offSpring = new ArrayList<>();

        for (int p1 = 0; p1 < parents.size(); p1++) {
            for (int p2 = p1+1; p2 < parents.size(); p2++) {
                if (rollDice(crossOverRate))
                {
                    Solution parent1 = parents.get(p1);
                    Solution parent2 = parents.get(p2);

                    List<Solution> children = crossOverOperator1.apply(problem, parent1, parent2);

                    offSpring.addAll(children);
                    updateBest(problem, children);
                }
            }
        }

        return offSpring;
    }

    @Override
    protected void _init(OptimizationProblem<PM, R> problem) {
        createInitialPopulation(problem);
        stagnationCount = 0;
        iterationCount = 0;
        setBestAchieveTime(0);
        setBestAchieveIteration(0);
        bestSolution = null;
        previousPopBest = Double.MAX_VALUE;
        mutationRate = initialMutationRate;
        migrantTurn = 0;

        sa = new SimulatedAnnealing<>(
                null,
                null,
                new PECTORandomSwapNG(),
                25,
                new GeometricUpdate(0.95)
        );

        ls = new PECTOLocalSearch(
                new IterationBasedTC<>(1),
                null,
                new PECTORandomISG((PECTOModel2) problem.model()),
                new PECTORandomSwapNG(),
                PECTOLocalSearch.Selection.FIRST_IMP
        );

        lahc = new PECTOLocalSearch(
                new IterationBasedTC<>(1000),
                null,
                new PECTORandomISG((PECTOModel2) problem.model()),
                new PECTORandomSwapNG(),
                PECTOLocalSearch.Selection.LAHC
        );
    }

    protected void createInitialPopulation(OptimizationProblem<PM, R> problem) {
        population = new ArrayList<>();
        PECTOHybridISG2 pectoHybridISG = new PECTOHybridISG2((PECTOModel2) problem.model());

        for (int i = 0; i < populationSize * 0.50; i++) {
            Solution s = isg.generate(problem);
            updateBest(problem,s);
            population.add(s);
        }

        for (int i = 0; i < populationSize * 0.25; i++) {
            Solution s = pectoHybridISG.generate((OptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
            updateBest(problem, s);
            population.add(s);
        }

        for (int i = 0; i < populationSize * 0.25; i++) {
            Solution t = greedyHeuristic.solve((PECTOModel2) problem.model(),
                    (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
            updateBest(problem, t);
            population.add(t);
        }
    }

    @Override
    public String toString() {
        return "GA " + "CR:" + crossOverRate + " " + crossOverOperator1 +
                " MR:" + mutationRate +
                " POP:" + populationSize +
                " SP:" + stagnationPeriod +
                " HYBRID:" + hybrid;
    }

    public void setIsg(InitialSolutionGenerator<PM, R> isg) {
        this.isg = isg;
    }

    public void setStagnationPeriod(int stagnationPeriod) {
        this.stagnationPeriod = stagnationPeriod;
    }

    public void setPopulationSize(int populationSize) {
        this.populationSize = populationSize;
    }

    public void setParentSelector(ParentSelector parentSelector) {
        this.parentSelector = parentSelector;
    }

    public void setVictimSelector(VictimSelector victimSelector) {
        this.victimSelector = victimSelector;
    }

    public void setCrossOverRate(double crossOverRate) {
        this.crossOverRate = crossOverRate;
    }

    public void setCrossOverOperator(CrossOverOperator crossOverOperator) {
        this.crossOverOperator1 = crossOverOperator;
    }

    public void setMutationRate(double mutationRate) {
        this.initialMutationRate = mutationRate;
        this.mutationRate = mutationRate;
    }

    public void adaptMutationRate(double factor) {
        mutationRate *= factor;
        mutationRate = Math.min(mutationRate, 0.75);
        mutationRate = Math.max(mutationRate, 0.05);
//        System.out.println("MR: " + mutationRate);
    }

    public void insertRandomSolution(OptimizationProblem<PM, R> problem) {
        sortPopulationByAge();

        Solution newSolution = isg.generate(problem);
        population.set((int)(populationSize * 0.1) + generateRandomInt(3), newSolution);
        updateBest(problem, newSolution);
        System.out.println("RANDOM: " + newSolution.value());

    }

    public void insertGreedySolution(OptimizationProblem<PM, R> problem) {
        sortPopulationByAge();

        Solution greedySolution = greedyHeuristic.solve((PECTOModel2) problem.model(),
                (SimpleOptimizationProblem<PECTOModel2, IntegerPermutation>) problem);

//        population.set((int)(populationSize * 0.1) + generateRandomInt((int)(populationSize * 0.1)), greedySolution);
        population.set((int)(populationSize * 0.1) + generateRandomInt(3), greedySolution);
        updateBest(problem, greedySolution);
        System.out.println("GREEDY: " + greedySolution.value());

    }

    private void insertHybridSolution(OptimizationProblem<PM,R> problem) {
        sortPopulationByAge();

        PECTOHybridISG2 pectoHybridISG = new PECTOHybridISG2((PECTOModel2) problem.model());
        PectoSolution hybridSolution = (PectoSolution) pectoHybridISG.generate((OptimizationProblem<PECTOModel2, IntegerPermutation>) problem);
//        population.set((int)(populationSize * 0.1) + generateRandomInt((int)(populationSize * 0.1)), hybridSolution);
        population.set((int)(populationSize * 0.1) + generateRandomInt(3), hybridSolution);
        updateBest(problem, hybridSolution);
        System.out.println("HYBRID: " + hybridSolution.value());
    }

    private void sortPopulationByAge() {
        population.sort((s1, s2) -> {
            int a1 = ((PectoSolution) s1).getAge();
            int a2 = ((PectoSolution) s2).getAge();
            return Integer.compare(a2, a1);
        });
    }
    private void getIntegerPermutationGA(PECTOGA<PECTOModel2, IntegerPermutation> pectoga, PECTOModel2 pectoModel2,
                                         int popSize, MutationOperation mutationOperation) {
        pectoga.setIsg(new PECTORandomISG(pectoModel2));
        pectoga.setPopulationSize(popSize);
//        pectoga.setParentSelector(new TournamentSelection());
        pectoga.setVictimSelector(new FitnessBasedVictimSelector());
        pectoga.setCrossOverRate(0.75);
//        pectoga.setCrossOverOperator(new PECTOCrossOverGreedyOX(0.9));
        pectoga.setMutationRate(0.05);
        pectoga.setMoveMutation(mutationOperation);
    }

    public static void main(String[] args) {
        PECTOModel2 pectoModel2 = new PECTOModel2(200, 2430, 64);
        SimpleOptimizationProblem<PECTOModel2, IntegerPermutation> problem =
                new SimpleOptimizationProblem<>(pectoModel2, new PECTOMinimumTimeOF());
        PECTOGA<PECTOModel2, IntegerPermutation> pectoga = new PECTOGA(new TimeBasedTC(6000), false);
        pectoga.getIntegerPermutationGA(pectoga, pectoModel2, 40, new PECTOMutationMove(true));
        pectoga._init(problem);
        pectoga._perform(problem);
        System.out.println("T: " + ((PectoSolution)pectoga.bestSolution).transportPenalty());
        System.out.println("V: " + ((PectoSolution)pectoga.bestSolution).standardDeviationPenalty());
        System.out.println("C: " + ((PectoSolution)pectoga.bestSolution).getTotalCost());
        System.out.println("F: " + pectoga.bestSolution.value());
    }
}
