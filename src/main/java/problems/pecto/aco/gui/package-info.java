/**
 * PECTOACO diagnosis: a desktop tool that shows a PECTOACO run as it works, live or step by step, forward and back
 * ({@link problems.pecto.aco.gui.PECTOACODiagnosis}).
 * <p>
 * <b>The engine is a copy of PECTOACO.</b> {@code ColonyRun}, {@code DiagnosticAnt}, {@code PheromoneTrails} and
 * {@code Candidates} copy problems.pecto.aco.PECTOACO, Ant, Pheromone and CandidateLists line for line, so that the
 * algorithm's own code stays free of diagnosis code. The copy adds events (the probe decides which of them it wants as
 * {@code Frame}s; only then are decisions recorded, and recording only reads) and checkpoints at the start of
 * iterations. {@code DiagnosticColonyTest} runs the copy and PECTOACO side by side and requires the same runs, bit for
 * bit: <b>when PECTOACO changes, change the copy the same way</b> until that test passes.
 * <p>
 * <b>Going back</b> works by determinism: {@code RunController} restores the nearest checkpoint (with the exact RNG
 * states, {@code CheckpointRNG}) and replays silently to the wanted event.
 * <p>
 * <b>Views</b>: {@code MapView} (the instance, routes, decisions, pheromone), {@code DecisionPanel},
 * {@code ConstructionPanel}, {@code IterationPanel}, {@code PheromonePanel}, {@code NodePanel} and
 * {@code HistoryCharts}, all fed the immutable {@code Frame} of the current event; {@code Practicality} limits what
 * is shown on large instances.
 */
package problems.pecto.aco.gui;
