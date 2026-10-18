package core.algorithm.aco.problem.wsn;


import core.algorithm.SimpleSolution;
import core.algorithm.localsearch.SolutionGenerator;
import core.base.OptimizationProblem;
import core.base.Solution;
import core.problems.wsn.WSN;
import core.representation.BitString;
import core.utils.random.RNG;
import core.utils.random.RandUtils;

public class WSNRandomSG implements SolutionGenerator {
    @Override
    public Solution generate(OptimizationProblem problem) {
        WSN wsn = (WSN) problem.model();
        BitString bs = new BitString(wsn.getSolutionSize());
        RNG rng = RandUtils.getDefaultRNG();

        for (int i = 0; i < wsn.getSolutionSize(); i++) {
            bs.set(i,rng.randBoolean());
        }
        return new SimpleSolution(bs,problem.objectiveValue(bs));
    }


}
