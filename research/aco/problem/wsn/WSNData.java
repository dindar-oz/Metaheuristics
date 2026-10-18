package core.algorithm.aco.problem.wsn;

import core.problems.wsn.Point2D;
import core.problems.wsn.WSN;
import core.representation.BitString;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Index-based, immutable view of a {@link WSN} instance: targets and potential sensor
 * positions are identified by their array index, and the coverage / communication
 * relations are stored as sorted {@code int[]} adjacency lists.
 * <p>
 * Instances are thread-safe and can be shared; use {@link #of(WSN)} so that all ants of a
 * colony share one copy instead of building their own.
 */
public final class WSNData {

    private static final Map<WSN, WSNData> CACHE = Collections.synchronizedMap(new WeakHashMap<>());

    private final int m;
    private final int k;
    private final int targetCount;
    private final int positionCount;
    private final Point2D[] potentialPositions;

    /** targetToPositions[t] = positions whose sensor would cover target t. */
    private final int[][] targetToPositions;
    /** positionToTargets[p] = targets covered by a sensor at position p. */
    private final int[][] positionToTargets;
    /** positionToPositions[p] = positions within communication range of p (excluding p). */
    private final int[][] positionToPositions;

    // Set views of the same relations, kept for callers that work with HashSet-based states.
    private final HashMap<Integer, Set<Integer>> targetToPositionSets = new HashMap<>();
    private final HashMap<Integer, Set<Integer>> positionToPositionSets = new HashMap<>();

    /** Returns the shared data for {@code wsn}, building it on first use. */
    public static WSNData of(WSN wsn) {
        return CACHE.computeIfAbsent(wsn, WSNData::new);
    }

    public WSNData(WSN wsn) {
        m = wsn.getM();
        k = wsn.getK();
        Point2D[] targets = wsn.getTargets();
        potentialPositions = wsn.getPotentialPositions();
        targetCount = targets.length;
        positionCount = potentialPositions.length;
        HashMap<Point2D, Integer> positionIndex = wsn.getPotentialPositionToIndexMap();

        targetToPositions = new int[targetCount][];
        for (int t = 0; t < targetCount; t++) {
            targetToPositions[t] = toSortedIndices(wsn.getTargetToPotentialPositionMap().get(targets[t]), positionIndex);
        }

        positionToPositions = new int[positionCount][];
        for (int p = 0; p < positionCount; p++) {
            positionToPositions[p] = toSortedIndices(
                    wsn.getPotentialPositionToPotentialPositionMap().get(potentialPositions[p]), positionIndex);
        }

        // Invert target->positions so that both directions are guaranteed to agree.
        int[] degree = new int[positionCount];
        for (int[] positions : targetToPositions)
            for (int p : positions) degree[p]++;
        positionToTargets = new int[positionCount][];
        for (int p = 0; p < positionCount; p++) positionToTargets[p] = new int[degree[p]];
        int[] fill = new int[positionCount];
        for (int t = 0; t < targetCount; t++)
            for (int p : targetToPositions[t]) positionToTargets[p][fill[p]++] = t;

        for (int t = 0; t < targetCount; t++)
            targetToPositionSets.put(t, Collections.unmodifiableSet(toSet(targetToPositions[t])));
        for (int p = 0; p < positionCount; p++)
            positionToPositionSets.put(p, Collections.unmodifiableSet(toSet(positionToPositions[p])));
    }

    private static int[] toSortedIndices(Set<Point2D> points, Map<Point2D, Integer> index) {
        int[] result = points.stream().mapToInt(index::get).toArray();
        Arrays.sort(result);
        return result;
    }

    private static Set<Integer> toSet(int[] values) {
        Set<Integer> set = new HashSet<>();
        for (int v : values) set.add(v);
        return set;
    }

    // ------------------------------------------------------------------ index-based API

    public int getK() {
        return k;
    }

    public int getM() {
        return m;
    }

    public int targetsSize() {
        return targetCount;
    }

    public int positionsSize() {
        return positionCount;
    }

    public Point2D[] getPotentialPositions() {
        return potentialPositions;
    }

    /** Positions covering target {@code t}, ascending. Do not modify the returned array. */
    public int[] coveringPositions(int t) {
        return targetToPositions[t];
    }

    /** Targets covered from position {@code p}, ascending. Do not modify the returned array. */
    public int[] coveredTargets(int p) {
        return positionToTargets[p];
    }

    /** Positions within communication range of {@code p}, ascending. Do not modify. */
    public int[] neighbours(int p) {
        return positionToPositions[p];
    }

    // ------------------------------------------------------------------ set-based API

    /** @return number of sensors in {@code sensors} that cover {@code target} */
    public int coverage(Integer target, Set<Integer> sensors) {
        int count = 0;
        for (int p : targetToPositions[target])
            if (sensors.contains(p)) count++;
        return count;
    }

    /** @return number of sensors in {@code sensors} within communication range of {@code sensor} */
    public int connectivity(Integer sensor, Set<Integer> sensors) {
        int count = 0;
        for (int p : positionToPositions[sensor])
            if (sensors.contains(p)) count++;
        return count;
    }

    public Set<Integer> getCoveringPositions(int target) {
        return targetToPositionSets.get(target);
    }

    public Set<Integer> getConnectedPositions(int sensor) {
        return positionToPositionSets.get(sensor);
    }

    /** Same check as {@link WSN#isFeasible}: every target k-covered, every sensor m-connected. */
    public boolean isFeasible(BitString bs) {
        HashSet<Integer> sensors = bs.ones();
        for (int t = 0; t < targetCount; t++) {
            if (coverage(t, sensors) < k)
                return false;
        }
        for (Integer s : sensors) {
            if (connectivity(s, sensors) < m)
                return false;
        }
        return true;
    }
}
