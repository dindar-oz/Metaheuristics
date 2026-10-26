package alg.ga;

import problem.OptimizationProblem;
import problem.ProblemModel;
import representation.PectoSolution;
import representation.Representation;
import representation.Solution;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static utils.RandUtils.generateRandomDouble;


/**
 * TODO: Performs victim selection acc.to fitness (disproportional)
 */
public class FitnessBasedVictimSelector implements VictimSelector{
    @Override
    public <R extends Representation, PM extends ProblemModel<R>>
    List<Solution> select(OptimizationProblem<PM, R> problem,
                          List<Solution> population,
                          int count) {

        List<Solution> candidates = population.stream()
                .filter(s -> ((PectoSolution) s).getAge() >= 3)
                .collect(Collectors.toList());

        if (candidates.size() < count) {
            candidates = new ArrayList<>(population);
        }

        List<Solution> victims = new ArrayList<>();

        for (int k = 0; k < count && !candidates.isEmpty(); k++) {

            double totalFitness = candidates.stream()
                    .mapToDouble(Solution::value)
                    .sum();

            double r = generateRandomDouble(1) * totalFitness;

            double cumulative = 0;
            Solution chosen = null;

            for (Solution s : candidates) {
                cumulative += s.value();
                if (cumulative >= r) {
                    chosen = s;
                    break;
                }
            }

            if (chosen == null) {
                chosen = candidates.get(candidates.size() - 1);
            }

            victims.add(chosen);
            candidates.remove(chosen);
        }

        return victims;
    }
}
