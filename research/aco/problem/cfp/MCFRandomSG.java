package core.algorithm.aco.problem.cfp;


import core.algorithm.SimpleSolution;
import core.algorithm.localsearch.SolutionGenerator;
import core.base.OptimizationProblem;
import core.base.Representation;
import core.base.Solution;
import core.problems.coalitionFormation.MCFPModel;
import core.problems.coalitionFormation.MultiCoalition;
import core.representation.IntegerAssignment;
import core.utils.random.RNG;
import core.utils.random.RandUtils;

public class MCFRandomSG implements SolutionGenerator {
    @Override
    public Solution generate(OptimizationProblem problem) {
        MCFPModel mcfp = (MCFPModel) problem.model();
        int[] coalition = new int[mcfp.getAgentCount()];
        RNG rng = RandUtils.getDefaultRNG();

        for (int i = 0; i < mcfp.getAgentCount(); i++) {
            if (rng.randDouble() < 0.5) { // assign agent to a random coalition
                // Real tasks are 1..getTaskCount()-1 (task 0 is the dummy "unassigned" task).
                int selectedTask = rng.randInt(mcfp.getTaskCount()-1)+1;
                coalition[i] = selectedTask;
            }
            else{
                coalition[i] = 0; //Do not assign agent to any coalition
            }
        }
        Representation rep = new MultiCoalition(new IntegerAssignment(coalition),mcfp.getAgents(),mcfp.getTaskCount());
        return new SimpleSolution(rep,problem.objectiveValue(rep));
    }


}
