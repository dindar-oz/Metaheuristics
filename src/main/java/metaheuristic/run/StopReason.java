package metaheuristic.run;

public enum StopReason {
    // the run's stop condition was satisfied (budget, target cost, time limit, ...)
    CONDITION_MET,
    // the run was cancelled through its RunHandle or its thread was interrupted
    CANCELLED,
    // the algorithm ended by itself (e.g. SA reached its minimum temperature, local search hit a local optimum)
    ALGORITHM_FINISHED
}
