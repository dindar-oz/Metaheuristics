package problems.pecto.aco.gui;

import problems.pecto.PECTOSolution;

import java.util.List;

/**
 * What the diagnosis shows at one event of a run: built by the engine at the event, then only read (by the Swing
 * thread). Arrays are copies that nothing writes.
 *
 * @param seq          the event's number in the run (1 = START); the same in every replay
 * @param ant          the ant being built or just built, -1 outside the ants
 * @param step         construction steps done by that ant (patients picked up)
 * @param ants         the ants of this iteration built so far
 * @param improvement  the improvement of the iteration best, once done
 * @param deposit      the pheromone update, once done
 * @param history      every completed iteration
 * @param pheromone    the pheromone now
 * @param construction the state of the ant's construction, inside an ant
 * @param decision     the decision of this event (AmbulanceDecision, PatientDecision, HospitalDecision, StepDone)
 */
record Frame(long seq, EventType type, int iteration, int ant, boolean explorer, int step, long evaluations,
             PECTOSolution best, double bestCost, List<AntResult> ants, Improvement improvement, Deposit deposit,
             List<IterationStats> history, PheromoneTrails.Snapshot pheromone, Construction construction,
             Object decision) {

    record AntResult(int index, boolean explorer, double cost, PECTOSolution solution) {
    }

    /** The iteration best, improved by local search and then given the optimal hospitals (NaN: not run). */
    record Improvement(int ant, boolean explorer, double start, int tried, int improving, double afterLocalSearch,
                       double afterHospitals, double result) {
    }

    /** Who deposited how much; the bounds and branching factor after the update. */
    record Deposit(boolean globalBest, double depositorCost, double amount, boolean explorerDeposited,
                   double explorerCost, double explorerAmount, double tauMax, double tauMinPatient,
                   double tauMinHospital, double branchingFactor) {
    }

    record IterationStats(int iteration, double best, double iterationBest, double antBest, double meanAnt,
                          double branchingFactor, double diversity, double duplicates, int explorerWins,
                          boolean explorerWon, long evaluations) {
    }

    /** The ant's construction state (see DiagnosticAnt); rateState holds DiagnosticAnt.EXACT, BOUND or UNKNOWN. */
    record Construction(int[] previous, int[] at, int[] pending, int[] pendingHospital, int[][] stops, int[] room,
                        boolean[] served, int[] remaining, byte[] rateState, double[] rating, int[] rated,
                        boolean[] ratedNearest) {
    }

    // ---- decisions

    /** One candidate patient of a rating: tau^alpha * eta^beta, eta = 1 / (w_q D(at, q)). */
    record CandidateScore(int q, boolean served, double tauA, double w, double d, double etaB, double score) {
    }

    /** How an ambulance's rating was computed; fallback: rated by the nearest unserved patient at tau_min. */
    record Rating(int previous, int at, int remaining, List<CandidateScore> candidates, CandidateScore fallback) {
    }

    /** One ambulance in the shortlist scan; NaN where a value does not exist. */
    record ScanRow(int a, byte stateBefore, double storedBefore, double threshold, String action, boolean rerated,
                   double rating, int ratedBy, Rating detail) {
    }

    record AmbulanceDecision(List<ScanRow> scan, int[] shortlist, int pick, int chosen) {
    }

    /** One candidate of a roulette draw; available = can be drawn (unserved patient, hospital with a free bed). */
    record Option(int id, boolean available, double tauA, double w, double d, double etaB, double weight) {
    }

    record PatientDecision(int ambulance, int previous, int at, boolean ignoredPheromone, List<Option> options,
                           double sum, double u, int chosen, boolean fallback) {
    }

    /** One hospital of a hospital draw. */
    record HospitalOption(int h, int room, boolean considered, double tauA, double dDeliver, double dNext, double cost,
                          double etaB, double weight) {
    }

    /** provisional: drawn on pickup (delivery only); else fixed when the next patient is known, replacing previous. */
    record HospitalDecision(boolean provisional, int ambulance, int patient, int next, int previous,
                            boolean ignoredPheromone, List<HospitalOption> options, double sum, double u, int chosen) {
    }

    record Invalidation(int ambulance, byte to, String why) {
    }

    record StepDone(int ambulance, int patient, int hospital, int[] candidateOf, List<Invalidation> invalidated) {
    }

    boolean insideAnt() {
        return construction != null;
    }
}
