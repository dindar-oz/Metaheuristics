package metaheuristic.ea.victimselector;

import representation.base.Representation;
import metaheuristic.ea.base.VictimSelector;
import representation.base.Individual;
import util.random.RNG;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects distinct victims; higher cost means higher selection probability.
 */
public class RouletteWheelVictimSelector implements VictimSelector {
    @Override
    public <S extends Representation<S>> List<Individual<S>> selectVictims(List<Individual<S>> population, int victimCount, RNG rng) {
        List<Individual<S>> candidates = new ArrayList<>(population);
        int count = Math.min(victimCount, candidates.size());
        List<Individual<S>> victims = new ArrayList<>(count);
        for (int c = 0; c < count; c++) {
            double[] costs = new double[candidates.size()];
            for (int i = 0; i < costs.length; i++)
                costs[i] = candidates.get(i).getCost();
            victims.add(candidates.remove(rng.rouletteSelect(costs)));
        }
        return victims;
    }

    @Override
    public String toString() {
        return "roulette";
    }
}
