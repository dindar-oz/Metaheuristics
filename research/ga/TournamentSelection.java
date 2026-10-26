package alg.ga;

import problem.OptimizationProblem;
import problem.ProblemModel;
import representation.Representation;
import representation.Solution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TournamentSelection implements ParentSelector{
    @Override
    public <R extends Representation, PM extends ProblemModel<R>> List<Solution> select(OptimizationProblem<PM, R> problem, List<Solution> population) {
        List<Solution> candidates = new ArrayList<>(population);
        List<Solution> selectedParents = new ArrayList<>();
        int parentNumber;

        if (population.size() <= 40) parentNumber = 5;
        else if (population.size() <= 80) parentNumber = 7;
        else if (population.size() <= 120) parentNumber = 9;
        else if (population.size() <= 160) parentNumber = 10;
        else parentNumber = 11;

        for (int i = 0; i < parentNumber; i++) {
            Collections.shuffle(candidates);

            Solution winner = candidates.stream()
                    .limit(parentNumber)
                    .min(Comparator.comparingDouble(Solution::value))
                    .orElseThrow();

            selectedParents.add(winner);
            candidates.remove(winner);
        }

        return selectedParents;
    }
}
