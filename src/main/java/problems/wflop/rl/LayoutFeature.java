package problems.wflop.rl;

/**
 * The features that describe an action of a ConstructiveWFLOP episode: placing a turbine in a free cell, or stopping.
 * All are intensive (per turbine or per cell), so a value means the same on farms of any size and with any number of
 * turbines, and a network trained on one farm can be applied to another. Feature f is element f.ordinal() of a row.
 */
public enum LayoutFeature {

    OWN_EFFICIENCY("power the new turbine would produce, relative to an unwaked turbine"),
    CAUSED_LOSS("power the placed turbines would lose, in unwaked turbine powers"),
    MARGINAL_POWER("power added (own minus caused loss), relative to the mean power of the placed turbines"),
    MARGINAL_COST("cost added, relative to the mean cost of the placed turbines: the objective improves exactly when "
            + "MARGINAL_POWER exceeds it"),
    IMPROVEMENT("the reward of the action times the number of turbines after it (rewards shrink as 1 / turbines)"),
    UPWIND_EXPOSURE("squared wake deficits the free cells could still cast on the cell (expected over wind states)"),
    DOWNWIND_EXPOSURE("squared wake deficits the cell would cast on the free cells (expected over wind states)"),
    EDGE_DISTANCE("distance to the farm edge, relative to 1 km and at most 1"),
    PROGRESS("turbines placed / maximum number of turbines"),
    DENSITY("turbines placed / cells"),
    EFFICIENCY("power of the placed turbines / power of as many unwaked turbines (1 for an empty farm)"),
    STOP("1 for the action that ends the layout, 0 otherwise");

    public static final int COUNT = values().length;

    private final String description;

    LayoutFeature(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
