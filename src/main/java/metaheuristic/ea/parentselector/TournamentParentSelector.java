package metaheuristic.ea.parentselector;

import representation.base.Representation;
import metaheuristic.ea.base.ParentSelector;
import representation.base.Individual;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects parentCount distinct individuals, each the best of tournamentSize random candidates.
 */
public class TournamentParentSelector implements ParentSelector {
    public static final int DEFAULT_TOURNAMENT_SIZE = 5;

    private final int tournamentSize;
    private final int parentCount; // <= 0 means half of the population

    public TournamentParentSelector(int tournamentSize, int parentCount) {
        if (tournamentSize < 1)
            throw new IllegalArgumentException("tournamentSize must be positive");
        this.tournamentSize = tournamentSize;
        this.parentCount = parentCount;
    }

    public TournamentParentSelector(int tournamentSize) {
        this(tournamentSize, 0);
    }

    public TournamentParentSelector() {
        this(DEFAULT_TOURNAMENT_SIZE, 0);
    }

    @Override
    public <S extends Representation<S>> List<Individual<S>> selectParents(List<Individual<S>> population, RNG rng) {
        List<Individual<S>> candidates = new ArrayList<>(population);
        int count = Math.min(parentCount > 0 ? parentCount : population.size() / 2, candidates.size());
        List<Individual<S>> parents = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            int size = Math.min(tournamentSize, candidates.size());
            int winner = -1;
            for (int index : rng.distinctInts(candidates.size(), size))
                if (winner < 0 || candidates.get(index).getCost() < candidates.get(winner).getCost())
                    winner = index;
            parents.add(candidates.remove(winner));
        }
        return parents;
    }

    @Override
    public String toString() {
        return "tournament(" + tournamentSize + ", " + (parentCount > 0 ? String.valueOf(parentCount) : "n/2") + ")";
    }
}
