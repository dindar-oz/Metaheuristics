package problems.pecto.aco.gui;

import metaheuristic.AbstractMetaheuristic;
import metaheuristic.run.SearchContext;
import problems.pecto.PECTOProblem;
import problems.pecto.PECTOSolution;

/**
 * The diagnosis' copy of PECTOACO as an ordinary metaheuristic, so that it can be run like PECTOACO (e.g. by a
 * Runner) and compared with it: for the same settings, problem, seed and budget, both must give the same run.
 */
public final class DiagnosticColony extends AbstractMetaheuristic<PECTOSolution, PECTOProblem> {

    private final ColonySettings settings;

    public DiagnosticColony(ColonySettings settings) {
        super("PECTOACO (diagnosis)");
        settings.toPECTOACO();     // checks the values as PECTOACO does
        this.settings = settings;
    }

    @Override
    public void search(PECTOProblem problem, SearchContext<PECTOSolution> ctx) {
        new ColonyRun(settings, problem, new Candidates(problem, settings.candidates()), ctx, Probe.NONE).run();
    }

    @Override
    protected String parameters() {
        return settings.toString();
    }
}
