package problems.pecto.aco.gui;

import problems.pecto.PECTOProblem;

/**
 * What the diagnosis shows for an instance of this size. Everything works on small instances; on large ones the views
 * that would be unreadable or slow are limited or switched off, and {@link #note()} says which.
 *
 * @param labels       participant IDs on the map
 * @param edgeLimit    the patient pheromone edges drawn on the map: all, or the strongest ones
 * @param heatMap      the patient x hospital pheromone heat map
 * @param liveTables   tables of every ambulance updated while playing (always when paused)
 */
record Practicality(boolean small, boolean labels, int edgeLimit, boolean heatMap, boolean liveTables) {

    static final int SMALL_PATIENTS = 300, SMALL_AMBULANCES = 60;

    static Practicality of(PECTOProblem p) {
        int patients = p.patientCount(), all = p.participantCount();
        boolean small = patients <= SMALL_PATIENTS && p.ambulanceCount() <= SMALL_AMBULANCES;
        return new Practicality(small, all <= 200, small ? Integer.MAX_VALUE : 3000, patients * p.hospitalCount() <= 40_000,
                p.ambulanceCount() <= 100);
    }

    String note() {
        if (small)
            return "";
        StringBuilder sb = new StringBuilder("Large instance:");
        if (!labels)
            sb.append(" no labels on the map;");
        sb.append(" the map draws the ").append(edgeLimit).append(" strongest pheromone edges;");
        if (!heatMap)
            sb.append(" hospital pheromone heat map off;");
        if (!liveTables)
            sb.append(" ambulance tables refresh when paused;");
        sb.append(" stepping through ants and decisions works, but playing them takes long.");
        return sb.toString();
    }
}
