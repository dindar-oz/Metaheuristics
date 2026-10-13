package lab.problemlab.pecto;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import lab.problemlab.ProblemWorkbench;
import lab.problemlab.model.ComponentSpec;
import lab.problemlab.model.Evaluation;
import lab.problemlab.model.FileFormat;
import lab.problemlab.model.Workspace;
import lab.problemlab.ui.ProblemView;
import lab.problemlab.ui.ViewHost;
import problems.pecto.PECTOFiles;
import problems.pecto.PECTOInstances;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;
import problems.pecto.operators.ChangeHospital;
import problems.pecto.operators.GreedyPECTOSG;
import problems.pecto.operators.MoveTrip;
import problems.pecto.operators.RandomPECTOSG;
import problems.pecto.operators.SwapTrips;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static lab.model.ParamSpec.choice;
import static lab.model.ParamSpec.integer;

/**
 * Post-earthquake casualty transport (Balci &amp; Oz) in the Problem Lab: the paper's instances or random ones, greedy,
 * random or empty solutions, the PECTO mutation operators, text files ({@link PECTOFiles}) and an interactive map
 * ({@link PECTOEditor}) on which participants and trips are edited with the mouse and the keyboard.
 */
public final class PECTOWorkbench implements ProblemWorkbench<PECTOSolution, PECTOProblem> {

    // where the blank instance puts its first ambulance and hospital
    private static final double CENTER = PECTOInstances.AREA_SIZE / 2;

    @Override
    public String name() {
        return "Casualty transport (PECTO)";
    }

    @Override
    public String description() {
        return "Ambulances take patients, one at a time, to hospitals of limited capacity; every leg costs its length "
                + "times the urgency weight of its patient (Balci & Oz).";
    }

    @Override
    public List<ComponentSpec<Void, PECTOProblem>> instanceGenerators() {
        String[] tableI = IntStream.rangeClosed(1, PECTOInstances.INSTANCE_COUNT)
                .mapToObj(k -> k + " (" + PECTOInstances.patientCount(k) + " patients)").toArray(String[]::new);
        return List.of(
                ComponentSpec.generator("Random (uniform)",
                        "Participants placed uniformly in a 350 × 350 area, urgencies drawn uniformly, every hospital with the same capacity.",
                        List.of(integer("ambulances", 8, 1, 100_000, "number of ambulances"),
                                integer("patients", 60, 0, 1_000_000, "number of patients"),
                                integer("hospitals", 5, 1, 100_000, "number of hospitals"),
                                integer("beds per hospital", 15, 0, 1_000_000, "capacity of every hospital"),
                                integer("seed", 42, 0, Integer.MAX_VALUE, "seed of the coordinates and urgencies")),
                        v -> PECTOInstances.random(v.getInt("ambulances"), v.getInt("patients"), v.getInt("hospitals"),
                                v.getInt("beds per hospital"), v.getInt("seed"))),
                ComponentSpec.generator("Table I (paper)",
                        "The paper's instances: 200 ambulances, 64 hospitals of 81 beds and 2430–5163 patients.",
                        List.of(choice("instance", tableI[0], "instance of Table I", tableI),
                                integer("seed", 42, 0, Integer.MAX_VALUE, "seed of the coordinates and urgencies")),
                        v -> PECTOInstances.paper(Integer.parseInt(v.getChoice("instance").split(" ")[0]), v.getInt("seed"))),
                ComponentSpec.generator("Blank area",
                        "One ambulance and one hospital in an empty area: add participants on the map to build an instance by hand.",
                        List.of(integer("beds", 10, 0, 1_000_000, "capacity of the hospital")),
                        v -> PECTOProblem.builder()
                                .ambulance(CENTER - 100, CENTER)
                                .hospital(CENTER + 100, CENTER, v.getInt("beds"))
                                .build()));
    }

    @Override
    public List<ComponentSpec<PECTOProblem, InitialSolutionGenerator<PECTOSolution>>> solutionGenerators() {
        return List.of(
                ComponentSpec.of("Greedy (nearest)",
                        "Most urgent patients first, each by the ambulance whose route ends nearest, to the nearest hospital with a free bed.",
                        GreedyPECTOSG::new),
                ComponentSpec.of("Random feasible",
                        "Patients in random order, each by a random ambulance to a random hospital with a free bed.",
                        RandomPECTOSG::new),
                ComponentSpec.of("Empty (no trips)",
                        "No ambulance moves: build the routes by hand on the map.",
                        problem -> (p, rng) -> problem.emptySolution()));
    }

    @Override
    public List<ComponentSpec<PECTOProblem, MutationOperator<PECTOSolution>>> mutations() {
        return List.of(
                ComponentSpec.of("MoveTrip", "Moves a random trip, with its hospital, to a random position of a random route.",
                        problem -> new MoveTrip()),
                ComponentSpec.of("ChangeHospital", "Delivers the patient of a random trip to another hospital with a free bed.",
                        problem -> new ChangeHospital()),
                ComponentSpec.of("SwapTrips", "Exchanges two random trips, each with its hospital, within or between routes.",
                        problem -> new SwapTrips()));
    }

    @Override
    public Optional<FileFormat<Void, PECTOProblem>> instanceFormat() {
        return Optional.of(FileFormat.text("PECTO instance", PECTOFiles.INSTANCE_EXTENSION,
                (nothing, in) -> PECTOFiles.readInstance(in),
                (nothing, problem, out) -> PECTOFiles.writeInstance(problem, out)));
    }

    @Override
    public Optional<FileFormat<PECTOProblem, PECTOSolution>> solutionFormat() {
        return Optional.of(FileFormat.text("PECTO solution", PECTOFiles.SOLUTION_EXTENSION,
                PECTOFiles::readSolution,
                (problem, solution, out) -> PECTOFiles.writeSolution(solution, out)));
    }

    @Override
    public Evaluation evaluate(PECTOProblem problem, PECTOSolution solution) {
        PECTOFacts facts = new PECTOFacts(problem, solution);
        Evaluation.Builder e = Evaluation.builder(facts.cost, problem.isFeasible(solution));
        if (facts.missing > 0)
            e.violation(facts.missing + " patient(s) not transported (Eq. 12)");
        if (facts.duplicated > 0)
            e.violation(facts.duplicated + " patient(s) transported more than once (Eq. 12)");
        if (facts.overfull > 0) {
            List<String> over = new ArrayList<>();
            for (int k = 0; k < problem.hospitalCount(); k++) {
                int h = problem.hospitalId(k);
                if (problem.room(solution, h) < 0 && over.size() < 4)
                    over.add(over.size() == 3 ? "…" : h + " (+" + -problem.room(solution, h) + ")");
            }
            e.violation(facts.overfull + " hospital(s) over capacity (Eq. 13): " + String.join(", ", over));
        }
        int trips = solution.tripCount();
        return e.metric("trips", trips + " / " + problem.patientCount())
                .metric("ambulances used", facts.busy + " / " + problem.ambulanceCount())
                .metric("longest route", facts.longest + " trips")
                .metric("beds used", trips + " / " + problem.totalCapacity())
                .metric("cost per trip", trips == 0 ? "–" : String.format("%.2f", facts.cost / trips))
                .build();
    }

    @Override
    public String describe(PECTOProblem problem) {
        return "%d ambulances, %d patients, %d hospitals (%d beds)".formatted(problem.ambulanceCount(),
                problem.patientCount(), problem.hospitalCount(), problem.totalCapacity());
    }

    @Override
    public ProblemView<PECTOSolution, PECTOProblem> createView(Workspace<PECTOSolution, PECTOProblem> workspace, ViewHost host) {
        return new PECTOEditor(workspace, host);
    }

    @Override
    public String toString() {
        return name();
    }
}
