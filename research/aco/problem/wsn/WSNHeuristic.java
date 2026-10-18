package core.algorithm.aco.problem.wsn;

import core.utils.random.RNG;

/**
 * Repair heuristic that proposes which sensors an ant may switch on next.
 * <ol>
 *   <li><b>Coverage repair</b>: for each target that is not yet k-covered (visited in a random
 *       order that is reshuffled once per construction), propose idle covering positions until
 *       the target would be k-covered.</li>
 *   <li><b>Connectivity repair</b>: if every target is covered, then for each active sensor with
 *       fewer than m active neighbours, propose its idle neighbour that already has the most
 *       active neighbours.</li>
 * </ol>
 * One instance per ant (it holds the ant's target order and scratch buffers).
 */
final class WSNHeuristic {

    private final WSNData data;
    private final RNG rng;
    private final int[] targetOrder;
    /** Positions already proposed in the current call. */
    private final boolean[] proposed;

    WSNHeuristic(WSNData data, RNG rng) {
        this.data = data;
        this.rng = rng;
        this.targetOrder = new int[data.targetsSize()];
        for (int t = 0; t < targetOrder.length; t++) targetOrder[t] = t;
        this.proposed = new boolean[data.positionsSize()];
    }

    /** Draws a new random target order (Fisher-Yates); call once per construction. */
    void reshuffle() {
        for (int i = targetOrder.length - 1; i > 0; i--) {
            int j = rng.randInt(i + 1);
            int tmp = targetOrder[i];
            targetOrder[i] = targetOrder[j];
            targetOrder[j] = tmp;
        }
    }

    /**
     * Writes the proposed sensors into {@code out} and returns how many there are.
     * Zero means the heuristic cannot improve {@code state} any further.
     */
    int candidates(WSNConstructionState state, int[] out) {
        int count = coverageCandidates(state, out);
        if (count == 0)
            count = connectivityCandidates(state, out);
        for (int i = 0; i < count; i++) proposed[out[i]] = false; // clear scratch marks
        return count;
    }

    private int coverageCandidates(WSNConstructionState state, int[] out) {
        int k = data.getK();
        int count = 0;
        for (int t : targetOrder) {
            int[] covering = data.coveringPositions(t);
            // Coverage including sensors already proposed in this call.
            int c = state.coverage(t);
            for (int p : covering)
                if (proposed[p]) c++;
            if (c >= k)
                continue;

            for (int p : covering) {
                if (!state.isOn(p) && !proposed[p]) {
                    proposed[p] = true;
                    out[count++] = p;
                    if (++c >= k) break;
                }
            }
        }
        return count;
    }

    private int connectivityCandidates(WSNConstructionState state, int[] out) {
        int m = data.getM();
        int count = 0;
        for (int s = 0; s < state.positionsSize(); s++) {
            if (!state.isOn(s) || state.connectivity(s) >= m)
                continue;

            // Idle neighbour with the most active neighbours (the most "useful" link).
            int best = -1;
            for (int nb : data.neighbours(s)) {
                if (state.isOn(nb)) continue;
                if (best < 0 || state.connectivity(nb) >= state.connectivity(best))
                    best = nb;
            }
            // best < 0: no idle neighbour, so this sensor cannot be repaired (this case used to
            // crash). proposed[best]: already suggested for another sensor.
            if (best >= 0 && !proposed[best]) {
                proposed[best] = true;
                out[count++] = best;
            }
        }
        return count;
    }
}
