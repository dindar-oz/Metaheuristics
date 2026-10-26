package problems.pecto.aco.gui;

/**
 * The points of a PECTOACO run at which the diagnosis can stop, each with its level. Stepping at a level stops at the
 * next event of that level or a coarser one.
 */
enum EventType {

    START(Level.ITERATION, "Start: greedy solution, pheromone at tau_max"),
    ANT_START(Level.STEP, "An ant starts"),
    AMBULANCE(Level.DECISION, "Ambulance drawn"),
    PATIENT(Level.DECISION, "Patient drawn"),
    FINAL_HOSPITAL(Level.DECISION, "Hospital fixed"),
    PROVISIONAL_HOSPITAL(Level.DECISION, "Provisional hospital drawn"),
    STEP_DONE(Level.STEP, "Construction step done"),
    ANT_DONE(Level.ANT, "Ant done"),
    IMPROVED(Level.ANT, "Iteration best improved"),
    PHEROMONE_UPDATED(Level.ANT, "Pheromone updated"),
    ITERATION_DONE(Level.ITERATION, "Iteration done");

    /** Coarse to fine. */
    enum Level {
        ITERATION("Iteration"), ANT("Ant"), STEP("Construction step"), DECISION("Decision");

        final String label;

        Level(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    final Level level;
    final String label;

    EventType(Level level, String label) {
        this.level = level;
        this.label = label;
    }

    // whether stepping at the given level stops here
    boolean stopsAt(Level stepping) {
        return level.ordinal() <= stepping.ordinal();
    }
}
