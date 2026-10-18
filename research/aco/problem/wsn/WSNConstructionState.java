package core.algorithm.aco.problem.wsn;

import core.representation.BitString;

import java.util.Arrays;

/**
 * Partial WSN solution built by an ant, with incrementally maintained feasibility counters.
 * <p>
 * Turning a sensor on costs O(degree), and {@link #isFeasible()} is O(1). The previous
 * implementation recomputed coverage and connectivity from scratch on every construction step.
 * Sensors are only ever switched on during construction, so the counters only increase.
 */
final class WSNConstructionState {

    private final WSNData data;
    private final boolean[] on;
    /** coverage[t] = number of active sensors covering target t. */
    private final int[] coverage;
    /** connectivity[p] = number of active sensors within communication range of position p. */
    private final int[] connectivity;

    private int activeCount;
    /** Targets whose coverage is still below k. */
    private int uncoveredTargets;
    /** Active sensors whose connectivity is still below m. */
    private int underConnectedSensors;

    WSNConstructionState(WSNData data) {
        this.data = data;
        on = new boolean[data.positionsSize()];
        coverage = new int[data.targetsSize()];
        connectivity = new int[data.positionsSize()];
        reset();
    }

    void reset() {
        Arrays.fill(on, false);
        Arrays.fill(coverage, 0);
        Arrays.fill(connectivity, 0);
        activeCount = 0;
        uncoveredTargets = data.getK() > 0 ? data.targetsSize() : 0;
        underConnectedSensors = 0;
    }

    void turnOn(int sensor) {
        if (on[sensor])
            return;
        on[sensor] = true;
        activeCount++;

        int k = data.getK();
        for (int t : data.coveredTargets(sensor)) {
            if (++coverage[t] == k)
                uncoveredTargets--;
        }

        int m = data.getM();
        for (int nb : data.neighbours(sensor)) {
            connectivity[nb]++;
            if (on[nb] && connectivity[nb] == m)
                underConnectedSensors--;   // neighbour just reached m links
        }
        if (connectivity[sensor] < m)
            underConnectedSensors++;       // the new sensor itself still lacks links
    }

    boolean isOn(int sensor) {
        return on[sensor];
    }

    int coverage(int target) {
        return coverage[target];
    }

    int connectivity(int position) {
        return connectivity[position];
    }

    boolean isFeasible() {
        return uncoveredTargets == 0 && underConnectedSensors == 0;
    }

    boolean allOn() {
        return activeCount == on.length;
    }

    int positionsSize() {
        return on.length;
    }

    BitString toBitString() {
        BitString bs = new BitString(on.length);
        for (int i = 0; i < on.length; i++) {
            if (on[i]) bs.set(i, true);
        }
        return bs;
    }
}
