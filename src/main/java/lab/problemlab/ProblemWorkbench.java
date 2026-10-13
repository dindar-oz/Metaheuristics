package lab.problemlab;

import base.InitialSolutionGenerator;
import base.MutationOperator;
import base.OptimizationProblem;
import lab.problemlab.model.ComponentSpec;
import lab.problemlab.model.Evaluation;
import lab.problemlab.model.FileFormat;
import lab.problemlab.model.Workspace;
import lab.problemlab.ui.ProblemView;
import lab.problemlab.ui.TextProblemView;
import lab.problemlab.ui.ViewHost;
import representation.base.Representation;

import java.util.List;
import java.util.Optional;

/**
 * Everything the Problem Lab needs to know about one optimization problem. The lab window
 * ({@link lab.problemlab.ui.ProblemLabFrame}) is generated from it: the instance generators, solution generators
 * and mutation operators fill the side panel with their parameters, the file formats enable opening and saving, and
 * {@link #createView} supplies the interactive picture of the instance and its solution.
 * <p>
 * To support a new problem, implement this interface and add it to {@link ProblemLabCatalog}. Only the name and the
 * component lists are required; without a view of its own a problem is shown as text.
 */
public interface ProblemWorkbench<S extends Representation<S>, P extends OptimizationProblem<S>> {

    String name();

    // one or two sentences for the problem chooser
    default String description() {
        return "";
    }

    // ways to create an instance from parameters (at least one, or an instance format)
    List<ComponentSpec<Void, P>> instanceGenerators();

    // ways to create a solution of the current instance
    List<ComponentSpec<P, InitialSolutionGenerator<S>>> solutionGenerators();

    // mutation operators that can be applied to the current solution
    List<ComponentSpec<P, MutationOperator<S>>> mutations();

    default Optional<FileFormat<Void, P>> instanceFormat() {
        return Optional.empty();
    }

    default Optional<FileFormat<P, S>> solutionFormat() {
        return Optional.empty();
    }

    // the cost and feasibility of a solution, with problem-specific violations and measures if available
    default Evaluation evaluate(P problem, S solution) {
        return Evaluation.of(problem, solution);
    }

    // a short description of an instance, e.g. for the window title
    default String describe(P problem) {
        return problem.toString();
    }

    // The interactive view of the workspace's instance and solution; called once per lab window, on the Swing thread
    default ProblemView<S, P> createView(Workspace<S, P> workspace, ViewHost host) {
        return new TextProblemView<>(this);
    }
}
