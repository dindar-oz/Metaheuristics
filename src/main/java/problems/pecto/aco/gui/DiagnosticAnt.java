package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The diagnosis' copy of problems.pecto.aco.Ant, which reports its construction to the run's probe. The construction
 * is the original's, line for line; the additions only read (when the probe wants a frame of an event, the decision
 * is recorded while it is made), so the draws, and with them the run, stay the same. Keep it identical to the original
 * (DiagnosticColonyTest checks the runs).
 */
final class DiagnosticAnt {

    static final byte EXACT = 0, BOUND = 1, UNKNOWN = 2;
    static final String[] STATE_NAMES = {"EXACT", "BOUND", "UNKNOWN"};

    private final ColonyRun run;
    final int index;
    final boolean explorer;
    private final PECTOProblem problem;
    private CheckpointRNG rng;
    private final int ambulances, patients, hospitals;
    private final double beta, explorerBeta;
    private final double explore;
    private final double[] ones;

    private final int[][] candidates;
    private final int[][] candidateOf;
    private final int[][] byDistance;
    private final int[][] hospitalOrder;
    private final Candidates lists;
    private final PheromoneTrails pheromone;
    private final double[][] patientWeight;
    private final double[][] hospitalWeight;

    private final boolean[] served;
    private final int[] room;
    private final int[] previous;
    private final int[] at;
    private final int[] pending;
    private final int[] pendingHospital;
    private final int[][] stops;
    private final int[] stopCount;
    private final int[] remaining;
    private final int[] nearestUnserved;

    private final double[] rating;
    private final int[] rated;
    private final byte[] rateState;
    private final boolean[] ratedNearest;
    private final int[] ratedHead;
    private final int[] ratedNext;
    private final int[] ratedPrev;

    private final int[] top;
    private final double[] choiceWeights;
    private final int[] choiceHospitals;

    // ---- diagnosis
    private boolean recording;          // the probe wants a frame of the decision being made
    int step;                           // construction steps done
    private Frame.Rating lastRating;
    private boolean lastIgnoredPheromone;
    private double lastU;
    private List<Frame.HospitalOption> lastHospitalOptions;
    private double lastHospitalSum;

    DiagnosticAnt(ColonyRun run, int index, boolean explorer, ColonySettings s, PECTOProblem problem, Candidates lists,
                  PheromoneTrails pheromone, CheckpointRNG rng) {
        this.run = run;
        this.index = index;
        this.explorer = explorer;
        this.problem = problem;
        this.rng = rng;
        this.explore = explorer ? s.explorerRate() : 0;
        ambulances = problem.ambulanceCount();
        patients = problem.patientCount();
        hospitals = problem.hospitalCount();
        beta = s.beta();
        explorerBeta = s.explorerBeta();
        if (explore > 0) {
            ones = new double[Math.max(s.candidates(), hospitals)];
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
        top = new int[Math.min(s.shortlist(), ambulances)];
        choiceHospitals = new int[Math.min(s.hospitalCandidates(), hospitals)];
        choiceWeights = new double[Math.max(choiceHospitals.length, s.candidates())];
    }

    CheckpointRNG rng() {
        return rng;
    }

    void rng(CheckpointRNG rng) {
        this.rng = rng;
    }

    PECTOSolution construct() {
        reset();
        step = 0;
        if (run.announce(EventType.ANT_START))
            run.deliver(null);
        for (int s = 0; s < patients; s++) {
            recording = run.announce(EventType.AMBULANCE);
            Frame.AmbulanceDecision ambulance = recording ? new Frame.AmbulanceDecision(new ArrayList<>(), null, -1, -1) : null;
            int a = chooseAmbulance(ambulance);
            recording = run.announce(EventType.PATIENT);
            int q = choosePatient(a);
            pickUp(a, q);
            step++;
        }
        int[][] result = new int[ambulances][];
        for (int a = 0; a < ambulances; a++) {
            if (pending[a] >= 0)
                append(a, pending[a], pendingHospital[a]);
            result[a] = Arrays.copyOf(stops[a], stopCount[a]);
        }
        recording = false;
        return problem.solution(result);
    }

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

    // the original chooseAmbulance(); with a decision to fill, the scan is recorded and the decision delivered
    private int chooseAmbulance(Frame.AmbulanceDecision record) {
        int count = 0;
        for (int a = 0; a < ambulances; a++) {
            byte before = rateState[a];
            double stored = before == UNKNOWN ? Double.NaN : rating[a];
            double threshold = count == top.length ? rating[top[count - 1]] : Double.NaN;
            Frame.Rating detail = null;
            boolean rerated = false;
            String action;
            if (rateState[a] == UNKNOWN) {
                rate(a);
                detail = lastRating;
            }
            if (count == top.length && rating[a] <= rating[top[count - 1]]) {
                action = rateState[a] == BOUND ? "ruled out by its bound" : "ruled out";
                if (record != null)
                    record.scan().add(new Frame.ScanRow(a, before, stored, threshold, action, false, rating[a],
                            rateState[a] == UNKNOWN ? -1 : rated[a], detail));
                continue;
            }
            if (rateState[a] == BOUND) {
                rate(a);
                detail = lastRating;
                rerated = true;
                if (count == top.length && rating[a] <= rating[top[count - 1]]) {
                    if (record != null)
                        record.scan().add(new Frame.ScanRow(a, before, stored, threshold, "ruled out after re-rating",
                                true, rating[a], rated[a], detail));
                    continue;
                }
            }
            int replaced = count == top.length ? top[count - 1] : -1;
            int i = count < top.length ? count++ : count - 1;
            while (i > 0 && rating[top[i - 1]] < rating[a]) {
                top[i] = top[i - 1];
                i--;
            }
            top[i] = a;
            if (record != null) {
                action = replaced >= 0 ? "into the shortlist, replacing A" + replaced : "into the shortlist";
                record.scan().add(new Frame.ScanRow(a, before, stored, threshold, action, rerated, rating[a], rated[a], detail));
            }
        }
        int pick = rng.randInt(count);
        if (record != null)
            run.deliver(new Frame.AmbulanceDecision(List.copyOf(record.scan()), Arrays.copyOf(top, count), pick, top[pick]));
        return top[pick];
    }

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
        Frame.CandidateScore fallback = null;
        if (bestPatient < 0) {
            bestPatient = nearestUnserved(at[a]);
            best = pheromone.minPatientWeight()
                    * eta(problem.urgencyWeight(bestPatient) * problem.distance(at[a], bestPatient), beta);
            if (recording)
                fallback = score(bestPatient, pheromone.minPatientWeight(), at[a]);
        }
        if (recording) {
            List<Frame.CandidateScore> rows = new ArrayList<>(c.length);
            for (int j = 0; j < c.length; j++)
                rows.add(score(c[j], w[j], at[a]));
            lastRating = new Frame.Rating(previous[a], at[a], remaining[previous[a]], rows, fallback);
        } else
            lastRating = null;
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

    private Frame.CandidateScore score(int q, double tauA, int from) {
        double w = problem.urgencyWeight(q), d = problem.distance(from, q), etaB = eta(w * d, beta);
        return new Frame.CandidateScore(q, served[patientIndex(q)], tauA, w, d, etaB, tauA * etaB);
    }

    private int nearestUnserved(int where) {
        int l = lists.location(where);
        int[] order = byDistance[l];
        while (served[patientIndex(order[nearestUnserved[l]])])
            nearestUnserved[l]++;
        return order[nearestUnserved[l]];
    }

    // the original ratingsAfterServing(), with the changes recorded
    private List<Frame.Invalidation> ratingsAfterServing(int q, int a, boolean record) {
        List<Frame.Invalidation> changes = record ? new ArrayList<>() : null;
        for (int b = ratedHead[patientIndex(q)]; b >= 0; b = ratedNext[b])
            if (rateState[b] == EXACT) {
                rateState[b] = ratedNearest[b] || remaining[previous[b]] > 0 ? BOUND : UNKNOWN;
                if (record)
                    changes.add(new Frame.Invalidation(b, rateState[b], ratedNearest[b] ? "rated by the nearest patient"
                            : remaining[previous[b]] > 0 ? "candidates left" : "no candidate left"));
            }
        rateState[a] = UNKNOWN;
        return changes;
    }

    // ---- patient

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
        List<Frame.Option> options = null;
        if (recording) {
            options = new ArrayList<>(c.length);
            for (int j = 0; j < c.length; j++) {
                double wq = problem.urgencyWeight(c[j]), d = problem.distance(at[a], c[j]);
                options.add(new Frame.Option(c[j], !served[patientIndex(c[j])], w[j], wq, d, eta(wq * d, b), choiceWeights[j]));
            }
        }
        if (!(sum > 0)) {
            if (recording)
                run.deliver(new Frame.PatientDecision(a, previous[a], at[a], free, options, sum, Double.NaN, rated[a], true));
            return rated[a];
        }
        int j = roulette(c.length, sum);
        int chosen = served[patientIndex(c[j])] ? rated[a] : c[j];
        if (recording)
            run.deliver(new Frame.PatientDecision(a, previous[a], at[a], free, options, sum, lastU, chosen, false));
        return chosen;
    }

    private void pickUp(int a, int q) {
        int p = pending[a];
        if (p >= 0) {
            int provisional = pendingHospital[a];
            recording = run.announce(EventType.FINAL_HOSPITAL);
            room[problem.index(pendingHospital[a])]++;
            int hospital = chooseHospital(p, q);
            room[problem.index(hospital)]--;
            append(a, p, hospital);
            if (recording) {
                pending[a] = -1;     // shown as fixed; set again below
                run.deliver(new Frame.HospitalDecision(false, a, p, q, provisional, lastIgnoredPheromone,
                        lastHospitalOptions, lastHospitalSum, lastU, hospital));
                pending[a] = p;
            }
        }
        recording = run.announce(EventType.PROVISIONAL_HOSPITAL);
        int hospital = chooseHospital(q, -1);
        room[problem.index(hospital)]--;
        pending[a] = q;
        pendingHospital[a] = hospital;
        previous[a] = q;
        at[a] = hospital;
        if (recording)
            run.deliver(new Frame.HospitalDecision(true, a, q, -1, -1, lastIgnoredPheromone, lastHospitalOptions,
                    lastHospitalSum, lastU, hospital));

        recording = run.announce(EventType.STEP_DONE);
        int k = patientIndex(q);
        served[k] = true;
        for (int n : candidateOf[k])
            remaining[n]--;
        List<Frame.Invalidation> changes = ratingsAfterServing(q, a, recording);
        if (recording) {
            step++;                 // the frame counts this step as done
            run.deliver(new Frame.StepDone(a, q, hospital, candidateOf[k].clone(), changes));
            step--;
        }
    }

    // ---- hospital

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
        if (recording)
            recordHospitals(p, next, w, b, count);
        lastIgnoredPheromone = free;
        lastHospitalSum = sum;
        lastU = Double.NaN;
        return problem.hospitalId(choiceHospitals[sum > 0 ? roulette(count, sum) : 0]);
    }

    // every hospital in the patient's order, with those the draw considers (the first `count` with a free bed)
    private void recordHospitals(int p, int next, double[] w, double b, int count) {
        int k = patientIndex(p);
        List<Frame.HospitalOption> rows = new ArrayList<>(hospitals);
        int considered = 0;
        for (int i = 0; i < hospitals; i++) {
            int h = hospitalOrder[k][i];
            int hospital = problem.hospitalId(h);
            double dDeliver = problem.distance(p, hospital);
            double dNext = next < 0 ? Double.NaN : problem.distance(hospital, next);
            double cost = problem.urgencyWeight(p) * dDeliver + (next < 0 ? 0 : problem.urgencyWeight(next) * dNext);
            boolean in = room[h] > 0 && considered < count;
            if (in)
                considered++;
            rows.add(new Frame.HospitalOption(hospital, room[h], in, w[h], dDeliver, dNext, cost, eta(cost, b),
                    in ? w[h] * eta(cost, b) : 0));
        }
        lastHospitalOptions = rows;
    }

    // ---- draws

    private int roulette(int n, double sum) {
        double u = rng.randDouble();
        lastU = u;
        double value = u * sum;
        for (int i = 0; i < n; i++) {
            value -= choiceWeights[i];
            if (value < 0)
                return i;
        }
        return n - 1;
    }

    private boolean explores() {
        return explore > 0 && (explore >= 1 || rng.randDouble() < explore);
    }

    static double eta(double cost, double b) {
        double c = Math.max(cost, ColonyRun.MIN_COST);
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

    private int patientIndex(int patient) {
        return patient - ambulances;
    }

    private void append(int a, int patient, int hospital) {
        if (stopCount[a] + 2 > stops[a].length)
            stops[a] = Arrays.copyOf(stops[a], 2 * stops[a].length);
        stops[a][stopCount[a]++] = patient;
        stops[a][stopCount[a]++] = hospital;
    }

    // ---- diagnosis: the construction as it is now

    Frame.Construction snapshot() {
        int[][] st = new int[ambulances][];
        for (int a = 0; a < ambulances; a++)
            st[a] = Arrays.copyOf(stops[a], stopCount[a]);
        byte[] state = rateState.clone();
        double[] r = rating.clone();
        int[] by = rated.clone();
        return new Frame.Construction(previous.clone(), at.clone(), pending.clone(), pendingHospital.clone(), st,
                room.clone(), served.clone(), remaining.clone(), state, r, by, ratedNearest.clone());
    }
}
