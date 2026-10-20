package problems.pecto.aco;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import util.random.RNG;

import java.util.Arrays;

/**
 * An ant: builds one solution per iteration with its own RNG, reading the candidate lists and the pheromone weights,
 * which nothing changes while it builds. It keeps the state of the solution it is building between the steps (and its
 * arrays between iterations). Used by one thread at a time.
 * <p>
 * Every ambulance starts at its position with no trips; each step moves one ambulance to one more patient: the
 * ambulance from the shortlist of best rated ones ({@link #chooseAmbulance}), the patient among the candidates of its
 * previous stop ({@link #choosePatient}), and the patient's hospital twice, provisionally on pickup and finally once
 * the next patient is known ({@link #pickUp}).
 * <p>
 * Naming: q, p, r and patient are patient IDs, k a patient index; hospital is a hospital ID, h a hospital index.
 */
final class Ant {

    private static final byte EXACT = 0, BOUND = 1, UNKNOWN = 2;

    private final PECTOProblem problem;
    private final RNG rng;
    private final int ambulances, patients, hospitals;
    private final double beta, explorerBeta;
    // the probability that a patient or hospital draw ignores the pheromone: every choice then has pheromone weight 1
    // (ones) and the heuristic exponent is explorerBeta. 0 for the colony's ants, whose ones is null
    private final double explore;
    private final double[] ones;

    // read only: the candidate lists and the pheromone weights of the iteration
    private final int[][] candidates;
    private final int[][] candidateOf;
    private final int[][] byDistance;
    private final int[][] hospitalOrder;
    private final CandidateLists lists;
    private final Pheromone pheromone;
    private final double[][] patientWeight;
    private final double[][] hospitalWeight;

    // the solution being built
    private final boolean[] served;             // by patient index
    private final int[] room;                   // free beds, by hospital index
    private final int[] previous;               // by ambulance: the ambulance itself or its last patient
    private final int[] at;                     // by ambulance: where it is, its position or a hospital ID
    private final int[] pending;                // by ambulance: its last patient, whose hospital is provisional; -1 if none
    private final int[] pendingHospital;
    private final int[][] stops;                // by ambulance: the trips fixed so far, patient and hospital IDs
    private final int[] stopCount;
    private final int[] remaining;              // by node: its unserved candidates
    private final int[] nearestUnserved;        // by location: skips the served patients at the front of byDistance[l]

    // An ambulance is rated by its most attractive next patient. While it stays where it is, serving patients can only
    // lower its rating, so a stale rating is an upper bound and is recomputed only if it could make the shortlist. It
    // is not a bound when the last candidate is served: the nearest unserved patient may be nearer.
    private final double[] rating;
    private final int[] rated;                  // the patient the rating is for
    private final byte[] rateState;             // EXACT, BOUND or UNKNOWN
    private final boolean[] ratedNearest;       // rated by the nearest unserved patient, no candidate being left
    // by patient index: the ambulances rated by the patient, linked through ratedNext / ratedPrev (-1 ends a list)
    private final int[] ratedHead;
    private final int[] ratedNext;
    private final int[] ratedPrev;

    // scratch space of the draws
    private final int[] top;                    // the shortlist, best first
    private final double[] choiceWeights;
    private final int[] choiceHospitals;        // hospital indices

    Ant(PECTOACO aco, PECTOProblem problem, CandidateLists lists, Pheromone pheromone, RNG rng, double explore) {
        this.problem = problem;
        this.rng = rng;
        this.explore = explore;
        ambulances = problem.ambulanceCount();
        patients = problem.patientCount();
        hospitals = problem.hospitalCount();
        beta = aco.beta;
        explorerBeta = aco.explorerBeta;
        if (explore > 0) {
            ones = new double[Math.max(aco.candidates, hospitals)];
            Arrays.fill(ones, 1);
        } else
            ones = null;

        candidates = lists.patients;
        candidateOf = lists.candidateOf;
        byDistance = lists.byDistance;
        hospitalOrder = lists.hospitalOrder;
        this.lists = lists;
        this.pheromone = pheromone;
        patientWeight = pheromone.patientWeight;
        hospitalWeight = pheromone.hospitalWeight;

        served = new boolean[patients];
        room = new int[hospitals];
        previous = new int[ambulances];
        at = new int[ambulances];
        pending = new int[ambulances];
        pendingHospital = new int[ambulances];
        stops = new int[ambulances][16];
        stopCount = new int[ambulances];
        remaining = new int[ambulances + patients];
        nearestUnserved = new int[byDistance.length];
        rating = new double[ambulances];
        rated = new int[ambulances];
        rateState = new byte[ambulances];
        ratedNearest = new boolean[ambulances];
        ratedHead = new int[patients];
        ratedNext = new int[ambulances];
        ratedPrev = new int[ambulances];
        top = new int[Math.min(aco.shortlist, ambulances)];
        choiceHospitals = new int[Math.min(aco.hospitalCandidates, hospitals)];
        choiceWeights = new double[Math.max(choiceHospitals.length, aco.candidates)];
    }

    PECTOSolution construct() {
        reset();
        for (int step = 0; step < patients; step++) {
            int a = chooseAmbulance();
            pickUp(a, choosePatient(a));
        }
        int[][] result = new int[ambulances][];
        for (int a = 0; a < ambulances; a++) {
            if (pending[a] >= 0)
                append(a, pending[a], pendingHospital[a]);
            result[a] = Arrays.copyOf(stops[a], stopCount[a]);
        }
        return problem.solution(result);
    }

    // every ambulance at its position with no trips, every patient unserved, every bed free
    private void reset() {
        Arrays.fill(served, false);
        for (int h = 0; h < hospitals; h++)
            room[h] = problem.capacity(problem.hospitalId(h));
        for (int a = 0; a < ambulances; a++) {
            previous[a] = a;
            at[a] = a;
            pending[a] = -1;
            stopCount[a] = 0;
            rateState[a] = UNKNOWN;
            rated[a] = -1;
        }
        Arrays.fill(ratedHead, -1);
        for (int n = 0; n < remaining.length; n++)
            remaining[n] = candidates[n].length;
        Arrays.fill(nearestUnserved, 0);
    }

    // ---- ambulance

    // drawn uniformly from the shortlist of best rated ambulances (ties: lower ID first)
    private int chooseAmbulance() {
        int count = 0;
        for (int a = 0; a < ambulances; a++) {
            if (rateState[a] == UNKNOWN)
                rate(a);
            if (count == top.length && rating[a] <= rating[top[count - 1]])
                continue;       // an upper bound is enough to rule it out
            if (rateState[a] == BOUND) {
                rate(a);
                if (count == top.length && rating[a] <= rating[top[count - 1]])
                    continue;
            }
            int i = count < top.length ? count++ : count - 1;
            while (i > 0 && rating[top[i - 1]] < rating[a]) {
                top[i] = top[i - 1];
                i--;
            }
            top[i] = a;
        }
        return top[rng.randInt(count)];
    }

    // the most attractive unserved candidate of the ambulance, or the nearest unserved patient if none is left
    private void rate(int a) {
        int[] c = candidates[previous[a]];
        double[] w = patientWeight[previous[a]];
        double best = -1;
        int bestPatient = -1;
        for (int j = 0; j < c.length && remaining[previous[a]] > 0; j++)
            if (!served[patientIndex(c[j])]) {
                double score = w[j] * eta(problem.urgencyWeight(c[j]) * problem.distance(at[a], c[j]), beta);
                if (score > best) {
                    best = score;
                    bestPatient = c[j];
                }
            }
        if (bestPatient < 0) {
            // all candidates served: the nearest unserved patient, at tau_min
            bestPatient = nearestUnserved(at[a]);
            best = pheromone.minPatientWeight()
                    * eta(problem.urgencyWeight(bestPatient) * problem.distance(at[a], bestPatient), beta);
        }
        rating[a] = best;
        if (rated[a] != bestPatient) {
            unlinkRated(a);
            rated[a] = bestPatient;
            int k = patientIndex(bestPatient);
            ratedPrev[a] = -1;
            ratedNext[a] = ratedHead[k];
            if (ratedHead[k] >= 0)
                ratedPrev[ratedHead[k]] = a;
            ratedHead[k] = a;
        }
        rateState[a] = EXACT;
        ratedNearest[a] = remaining[previous[a]] == 0;
    }

    // the unserved patient nearest to where an ambulance is (urgency-weighted)
    private int nearestUnserved(int where) {
        int l = lists.location(where);
        int[] order = byDistance[l];
        while (served[patientIndex(order[nearestUnserved[l]])])
            nearestUnserved[l]++;
        return order[nearestUnserved[l]];
    }

    // q has been served: the ratings for q are stale. Another ambulance's rating stays an upper bound while it still
    // has candidates (or was rated by the nearest patient anyway); otherwise its nearest patient may now be nearer.
    // Known gap, kept so that runs stay comparable with the recorded experiments: a BOUND is not revisited when the
    // ambulance's last candidate is served later, while it is rated by another patient. Its nearest patient may then
    // rate higher than the bound, and in about 0.15% of the steps on Table I instances the shortlist is not the exact
    // best. Fixing it changes the runs (see PECTOACOGoldenTest).
    private void ratingsAfterServing(int q, int a) {
        for (int b = ratedHead[patientIndex(q)]; b >= 0; b = ratedNext[b])
            if (rateState[b] == EXACT)
                rateState[b] = ratedNearest[b] || remaining[previous[b]] > 0 ? BOUND : UNKNOWN;
        rateState[a] = UNKNOWN;     // it moved
    }

    // ---- patient

    // an unserved candidate of the previous stop, drawn with probability ~ tau^alpha * eta^beta; if none is left, the
    // patient the ambulance was rated by (the nearest unserved one)
    private int choosePatient(int a) {
        int[] c = candidates[previous[a]];
        boolean free = explores();
        double[] w = free ? ones : patientWeight[previous[a]];
        double b = free ? explorerBeta : beta;
        double sum = 0;
        for (int j = 0; j < c.length; j++) {
            choiceWeights[j] = served[patientIndex(c[j])] ? 0
                    : w[j] * eta(problem.urgencyWeight(c[j]) * problem.distance(at[a], c[j]), b);
            sum += choiceWeights[j];
        }
        if (!(sum > 0))
            return rated[a];        // no candidate left: the nearest unserved patient
        int j = roulette(c.length, sum);
        // rounding can make the roulette fall through to the last candidate, which may be served (weight 0)
        return served[patientIndex(c[j])] ? rated[a] : c[j];
    }

    // the ambulance takes patient q: its pending patient gets its final hospital, q a provisional one
    private void pickUp(int a, int q) {
        int p = pending[a];
        if (p >= 0) {
            room[problem.index(pendingHospital[a])]++;
            int hospital = chooseHospital(p, q);
            room[problem.index(hospital)]--;
            append(a, p, hospital);
        }
        int hospital = chooseHospital(q, -1);
        room[problem.index(hospital)]--;
        pending[a] = q;
        pendingHospital[a] = hospital;
        previous[a] = q;
        at[a] = hospital;

        int k = patientIndex(q);
        served[k] = true;
        for (int n : candidateOf[k])
            remaining[n]--;
        ratingsAfterServing(q, a);
    }

    // ---- hospital

    // the hospital (ID) of patient p, followed by patient next (-1 if not known yet): drawn with probability
    // ~ tau^alpha * eta^beta, eta = 1 / (w_p D(p, h) + w_next D(h, next)), among the nearest hospitals with a free bed
    private int chooseHospital(int p, int next) {
        int k = patientIndex(p);
        boolean free = explores();
        double[] w = free ? ones : hospitalWeight[k];
        double b = free ? explorerBeta : beta;
        int count = 0;
        double sum = 0;
        for (int i = 0; i < hospitals && count < choiceHospitals.length; i++) {
            int h = hospitalOrder[k][i];
            if (room[h] == 0)
                continue;
            int hospital = problem.hospitalId(h);
            double cost = problem.urgencyWeight(p) * problem.distance(p, hospital)
                    + (next < 0 ? 0 : problem.urgencyWeight(next) * problem.distance(hospital, next));
            choiceHospitals[count] = h;
            choiceWeights[count++] = w[h] * eta(cost, b);
            sum += choiceWeights[count - 1];
        }
        // a bed is always left: the hospitals can take all patients, and every patient holds at most one
        return problem.hospitalId(choiceHospitals[sum > 0 ? roulette(count, sum) : 0]);
    }

    // ---- draws

    // an index below n drawn with probability proportional to choiceWeights, whose first n entries sum to sum
    private int roulette(int n, double sum) {
        double value = rng.randDouble() * sum;
        for (int i = 0; i < n; i++) {
            value -= choiceWeights[i];
            if (value < 0)
                return i;
        }
        return n - 1;
    }

    // whether the next patient or hospital draw ignores the pheromone; only an explorer draws for it
    private boolean explores() {
        return explore > 0 && (explore >= 1 || rng.randDouble() < explore);
    }

    // (1 / cost)^b; Math.pow is avoided for the usual exponents, as this is the innermost computation
    static double eta(double cost, double b) {
        double c = Math.max(cost, PECTOACO.MIN_COST);
        if (b == 2)
            return 1 / (c * c);
        if (b == 1)
            return 1 / c;
        if (b == 0.5)
            return 1 / Math.sqrt(c);
        if (b == 0)
            return 1;
        return Math.pow(c, -b);
    }

    // takes the ambulance out of the list of the patient it is rated by
    private void unlinkRated(int a) {
        if (rated[a] < 0)
            return;
        if (ratedPrev[a] >= 0)
            ratedNext[ratedPrev[a]] = ratedNext[a];
        else
            ratedHead[patientIndex(rated[a])] = ratedNext[a];
        if (ratedNext[a] >= 0)
            ratedPrev[ratedNext[a]] = ratedPrev[a];
    }

    // problem.index for a patient ID, without the type check: this is on the innermost paths
    private int patientIndex(int patient) {
        return patient - ambulances;
    }

    private void append(int a, int patient, int hospital) {
        if (stopCount[a] + 2 > stops[a].length)
            stops[a] = Arrays.copyOf(stops[a], 2 * stops[a].length);
        stops[a][stopCount[a]++] = patient;
        stops[a][stopCount[a]++] = hospital;
    }
}
