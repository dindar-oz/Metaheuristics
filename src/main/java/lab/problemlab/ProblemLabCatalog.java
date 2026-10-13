package lab.problemlab;

import lab.problemlab.pecto.PECTOWorkbench;

import java.util.List;

/**
 * The problems the Problem Lab offers. To support a new problem, implement a {@link ProblemWorkbench} for it and
 * add it here.
 */
public final class ProblemLabCatalog {

    private ProblemLabCatalog() {
    }

    public static List<ProblemWorkbench<?, ?>> workbenches() {
        return List.of(new PECTOWorkbench());
    }
}
