package problems.pecto.aco.gui;

import base.MutationOperator;
import metaheuristic.run.Metrics;
import metaheuristic.run.SearchContext;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.aco.PECTOACO;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.OptimalHospitals;
import problems.pecto.operators.SwapTrips;
import util.random.RNG;
import util.random.Seeds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One diagnosed run of PECTOACO: the iterations of problems.pecto.aco.PECTOACO (search, iterate, improve,
 * updatePheromone), line for line, with the ants built one after the other, plus events for the probe and
 * checkpoints at the start of every iteration. Keep it identical to the original (DiagnosticColonyTest checks the
 * runs). Confined to one thread.
 */
final class ColonyRun {

    // PECTOACO's constants
    static final int GLOBAL_BEST_PERIOD = 5;
    static final double MIN_COST = 1e-10;
    private static final List<MutationOperator<PECTOSolution>> MOVES = Arrays.asList(new MoveTrip(), new SwapTrips());

    private record Scored(PECTOSolution solution, double cost) {
    }

    private final ColonySettings s;
    private final PECTOProblem problem;
    private final SearchContext<PECTOSolution> ctx;
    private final Probe probe;
    private final Candidates lists;
    private PheromoneTrails pheromone;
    private List<DiagnosticAnt> ants;
    private Scored best;
    private int iteration;
    private int explorerWins;

    // ---- diagnosis
    private long seq;
    private EventType event;
    private DiagnosticAnt current;
    private final List<Frame.AntResult> antResults = new ArrayList<>();
    private Frame.Improvement improvement;
    private Frame.Deposit deposit;
    private final List<Frame.IterationStats> history = new ArrayList<>();

    ColonyRun(ColonySettings s, PECTOProblem problem, Candidates lists, SearchContext<PECTOSolution> ctx, Probe probe) {
        this.s = s;
        this.problem = problem;
        this.lists = lists;
        this.ctx = ctx;
        this.probe = probe;
    }

    // ---- PECTOACO.search

    // the whole run, as PECTOACO.search runs it
    void run() {
        if (start())
            iterate();
    }

    // up to the first iteration; false if the run ends there
    boolean start() {
        PECTOSolution greedy = GreedyPECTOSG.complete(problem.emptySolution());
        if (ctx.shouldStop())
            return false;
        best = new Scored(greedy, ctx.evaluate(greedy));
        if (problem.patientCount() == 0)
            return false;

        pheromone = new PheromoneTrails(s, problem, lists);
        pheromone.bound(best.cost());
        pheromone.fill();
        ants = ants(ctx.rng());
        if (announce(EventType.START))
            deliver(null);
        return true;
    }

    private List<DiagnosticAnt> ants(RNG rng) {
        long antSeed = Seeds.derive(rng.randInt(Integer.MAX_VALUE), rng.randInt(Integer.MAX_VALUE));
        List<DiagnosticAnt> list = new ArrayList<>(s.ants());
        for (int ant = 0; ant < s.ants(); ant++)
            list.add(new DiagnosticAnt(this, ant, isExplorer(ant), s, problem, lists, pheromone,
                    new CheckpointRNG(Seeds.derive(antSeed, ant))));
        return list;
    }

    private boolean isExplorer(int ant) {
        return ant >= s.ants() - s.explorers();
    }

    // ---- PECTOACO.iterate

    void iterate() {
        while (!ctx.shouldStop()) {
            probe.iterationStarts(iteration, seq, this::checkpoint);
            if (!iterateOnce())
                return;
            iteration++;
        }
    }

    // the body of PECTOACO.iterate's loop; false if the run stopped
    private boolean iterateOnce() {
        RNG rng = ctx.rng();
        antResults.clear();
        improvement = null;
        deposit = null;
        pheromone.weigh();
        Iteration built = buildAndEvaluate();
        if (built == null)
            return false;
        Scored iterationBest = improve(built, rng);
        if (iterationBest == null)
            return false;
        if (built.explorerWon)
            explorerWins++;
        if (iterationBest.cost() < best.cost()) {
            best = iterationBest;
            pheromone.bound(best.cost());
        }
        updatePheromone(iterationBest, built.explorerBest);
        ctx.iterationCompleted(metrics(built, iterationBest), built.solutions);

        Map<String, Double> m = ctx.progress().metrics();
        history.add(new Frame.IterationStats(iteration, best.cost(), iterationBest.cost(), built.best.cost(),
                built.meanCost(), m.getOrDefault(PECTOACO.BRANCHING_FACTOR, Double.NaN),
                m.getOrDefault(Metrics.DIVERSITY, Double.NaN), m.getOrDefault(Metrics.DUPLICATES, Double.NaN),
                explorerWins, built.explorerWon, ctx.progress().evaluations()));
        if (announce(EventType.ITERATION_DONE))
            deliver(null);
        return true;
    }

    private Iteration buildAndEvaluate() {
        Iteration built = new Iteration(s.ants());
        for (int ant = 0; ant < s.ants(); ant++) {
            if (ctx.shouldStop())
                return null;
            current = ants.get(ant);
            PECTOSolution solution = current.construct();
            problem.cost(solution);
            double cost = ctx.evaluate(solution);
            built.add(solution, cost, isExplorer(ant));
            antResults.add(new Frame.AntResult(ant, isExplorer(ant), cost, solution));
            if (announce(EventType.ANT_DONE))
                deliver(null);
        }
        current = null;
        return built;
    }

    private static final class Iteration {
        final List<PECTOSolution> solutions;
        Scored best, explorerBest;
        int bestAnt;
        boolean explorerWon;
        private double costSum;

        Iteration(int ants) {
            solutions = new ArrayList<>(ants);
        }

        void add(PECTOSolution solution, double cost, boolean explorer) {
            solutions.add(solution);
            costSum += cost;
            if (best == null || cost < best.cost()) {
                best = new Scored(solution, cost);
                bestAnt = solutions.size() - 1;
                explorerWon = explorer;
            }
            if (explorer && (explorerBest == null || cost < explorerBest.cost()))
                explorerBest = new Scored(solution, cost);
        }

        double meanCost() {
            return costSum / solutions.size();
        }
    }

    // ---- PECTOACO.improve

    private Scored improve(Iteration built, RNG rng) {
        Scored start = built.best;
        PECTOSolution solution = start.solution();
        double solutionCost = start.cost();
        int improving = 0;
        for (int step = 0; step < s.localSearchSteps(); step++) {
            if (ctx.shouldStop())
                return null;
            PECTOSolution neighbor = MOVES.get(rng.randInt(MOVES.size())).apply(solution, rng);
            double cost = ctx.evaluate(neighbor);
            if (cost < solutionCost) {
                solution = neighbor;
                solutionCost = cost;
                improving++;
            }
        }
        double afterLocalSearch = solutionCost, afterHospitals = Double.NaN;
        if (s.optimalHospitals()) {
            PECTOSolution reassigned = OptimalHospitals.of(solution);
            if (reassigned != solution) {
                if (ctx.shouldStop())
                    return null;
                double cost = ctx.evaluate(reassigned);
                afterHospitals = cost;
                if (cost <= solutionCost) {
                    solution = reassigned;
                    solutionCost = cost;
                }
            } else
                afterHospitals = solutionCost;
        }
        Scored result = solution == start.solution() ? start : new Scored(solution, solutionCost);
        improvement = new Frame.Improvement(built.bestAnt, built.explorerWon, start.cost(), s.localSearchSteps(),
                improving, afterLocalSearch, afterHospitals, result.cost());
        if (announce(EventType.IMPROVED))
            deliver(null);
        return result;
    }

    // ---- PECTOACO.updatePheromone

    private void updatePheromone(Scored iterationBest, Scored explorerBest) {
        boolean global = iteration % GLOBAL_BEST_PERIOD == GLOBAL_BEST_PERIOD - 1;
        Scored deposits = global ? best : iterationBest;
        pheromone.evaporate();
        pheromone.deposit(deposits.solution(), 1 / Math.max(deposits.cost(), MIN_COST));
        boolean explorerDeposits = s.explorerDeposit() > 0 && explorerBest != null && explorerBest.solution() != deposits.solution();
        if (explorerDeposits)
            pheromone.deposit(explorerBest.solution(), s.explorerDeposit() / Math.max(explorerBest.cost(), MIN_COST));
        pheromone.clamp();
        deposit = new Frame.Deposit(global, deposits.cost(), 1 / Math.max(deposits.cost(), MIN_COST), explorerDeposits,
                explorerBest == null ? Double.NaN : explorerBest.cost(),
                explorerDeposits ? s.explorerDeposit() / Math.max(explorerBest.cost(), MIN_COST) : 0,
                pheromone.tauMax(), pheromone.tauMinPatient(), pheromone.tauMinHospital(), pheromone.branchingFactor());
        if (announce(EventType.PHEROMONE_UPDATED))
            deliver(null);
    }

    private Map<String, Double> metrics(Iteration built, Scored iterationBest) {
        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put(Metrics.MEAN_COST, built.meanCost());
        metrics.put(Metrics.CURRENT_COST, iterationBest.cost());
        metrics.put(PECTOACO.BRANCHING_FACTOR, pheromone.branchingFactor());
        if (s.explorers() > 0)
            metrics.put(PECTOACO.EXPLORER_WINS, (double) explorerWins);
        return metrics;
    }

    // ---- diagnosis: events and frames

    // the next event; true if the probe wants its frame (then the caller records the decision and calls deliver)
    boolean announce(EventType type) {
        event = type;
        return probe.frameAt(type, ++seq, iteration);
    }

    // the frame of the event just announced
    void deliver(Object decision) {
        DiagnosticAnt ant = current;      // the ant being built, or just built (ANT_DONE); null outside the ants
        Frame.Construction construction = ant == null ? null : ant.snapshot();
        probe.deliver(new Frame(seq, event, iteration, ant == null ? -1 : ant.index, ant != null && ant.explorer,
                ant == null ? -1 : ant.step, ctx.progress().evaluations(), best.solution(), best.cost(),
                List.copyOf(antResults), improvement, deposit, Collections.unmodifiableList(new ArrayList<>(history)),
                pheromone.snapshot(), construction, decision));
    }

    // ---- diagnosis: checkpoints (the run's context must be a ColonyContext)

    Checkpoint checkpoint() {
        ColonyContext c = (ColonyContext) ctx;
        CheckpointRNG[] rngs = new CheckpointRNG[ants.size()];
        for (int i = 0; i < rngs.length; i++)
            rngs[i] = ants.get(i).rng().copy();
        return new Checkpoint(iteration, seq, explorerWins, best.solution(), best.cost(), pheromone.snapshot(),
                c.snapshot(), rngs, List.copyOf(history));
    }

    // this (new) run in the state of the checkpoint, ready to iterate()
    void restore(Checkpoint cp) {
        ((ColonyContext) ctx).restore(cp.context());
        pheromone = new PheromoneTrails(s, problem, lists);
        pheromone.restore(cp.pheromone());
        ants = new ArrayList<>(s.ants());
        for (int ant = 0; ant < s.ants(); ant++)
            ants.add(new DiagnosticAnt(this, ant, isExplorer(ant), s, problem, lists, pheromone, cp.antRngs()[ant].copy()));
        best = new Scored(cp.best(), cp.bestCost());
        iteration = cp.iteration();
        explorerWins = cp.explorerWins();
        seq = cp.seq();
        history.clear();
        history.addAll(cp.history());
    }
}
