package alg.ga;

import problem.OptimizationProblem;
import problem.ProblemModel;
import representation.Representation;
import representation.Solution;
import utils.RandUtils;
import java.util.ArrayList;
import java.util.List;


/**
 * TODO: Implement RouletteWheel Selection that selects individual acc. to its fitness
 *
 */
public class RouletteWheelSelection implements ParentSelector{
    @Override
    public <R extends Representation, PM extends ProblemModel<R>> List<Solution> select(OptimizationProblem<PM, R> problem, List<Solution> population) {
        List<Solution> selectedParents = new ArrayList<>();
        int parentNumber;
        double totalFitness = 0;
        double epsilon = 1e-8;

        for (Solution s : population) {
            totalFitness += 1.0 / (s.value() + epsilon);
        }

        if (population.size() <= 40) parentNumber = 5;
        else if (population.size() <= 80) parentNumber = 7;
        else if (population.size() <= 120) parentNumber = 9;        else if (population.size() <= 160) parentNumber = 10;
        else parentNumber = 11;

        for (int i = 0; i < parentNumber; i++) {
            double random = RandUtils.prob();
            double cumulativeProbabilitySum = 0;

            for (Solution s : population) {
                cumulativeProbabilitySum += (1.0 / (s.value() + epsilon)) / totalFitness;

                if (random <= cumulativeProbabilitySum && !selectedParents.contains(s)) {
                    selectedParents.add(s);
                    break;
                }
            }
        }

        return selectedParents;
    }
}
