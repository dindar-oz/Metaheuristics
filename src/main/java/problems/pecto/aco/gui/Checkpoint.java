package problems.pecto.aco.gui;

import problems.pecto.PECTOSolution;

import java.util.List;

/**
 * The state of a run at the start of an iteration, from which the run continues exactly as it did the first time.
 * Its RNGs are copied again when a run is restored, so one checkpoint can be restored any number of times.
 *
 * @param seq the number of the last event before the iteration
 */
record Checkpoint(int iteration, long seq, int explorerWins, PECTOSolution best, double bestCost,
                  PheromoneTrails.Snapshot pheromone, ColonyContext.Snapshot context, CheckpointRNG[] antRngs,
                  List<Frame.IterationStats> history) {

    long bytes() {
        return pheromone.bytes() + 3_000L * (antRngs.length + 1) + 100L * history.size();
    }
}
