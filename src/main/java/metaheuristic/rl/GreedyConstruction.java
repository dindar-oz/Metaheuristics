package metaheuristic.rl;

import metaheuristic.Metaheuristic;
import metaheuristic.run.SearchContext;
import representation.base.Representation;

/**
 * The greedy heuristic of a FeaturedConstructiveProblem: builds one solution, taking at every step the action with the
 * best known reward (FeaturedConstruction.actionRewards; the first one on ties), and finishes after that single
 * evaluation. It is where DQN with a reward baseline starts from, so it is the baseline its learning must beat.
 */
public final class GreedyConstruction<S extends Representation<S>> implements Metaheuristic<S, FeaturedConstructiveProblem<S>> {

    @Override
    public void search(FeaturedConstructiveProblem<S> problem, SearchContext<S> ctx) {
        FeaturedConstruction<S> construction = problem.start(ctx.rng());
        while (!construction.isComplete()) {
            if (ctx.shouldStop())
                return;
            int[] actions = construction.feasibleActions();
            double[] rewards = construction.actionRewards();
            if (rewards == null)
                throw new IllegalArgumentException(problem + " does not tell the rewards of its actions");
            int best = 0;
            for (int k = 1; k < rewards.length; k++)
                if (rewards[k] > rewards[best])
                    best = k;
            construction.step(actions[best]);
        }
        ctx.evaluate(construction.solution());
        ctx.iterationCompleted();
    }

    @Override
    public String getName() {
        return "Greedy";
    }
}
