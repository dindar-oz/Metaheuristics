package problems.pecto;

import java.util.Random;

/**
 * Synthetic PECTO instances, generated as in Section II.F of the paper from data of the 2020 Izmir earthquake:
 * 200 ambulances, 64 hospitals with 81 free beds each, and a number of patients that scales the 3,797 casualties
 * reported by AFAD by a multiplier (Table I). Every participant is placed uniformly at random in a 350 x 350 area.
 * <p>
 * The paper does not state how urgency levels are distributed; here every patient gets one of the five levels
 * uniformly at random. The same seed always gives the same instance.
 */
public final class PECTOInstances {

    public static final int AMBULANCES = 200;          // Izmir Provincial Health Directorate
    public static final int HOSPITALS = 64;            // Health Statistics Yearbook 2024
    public static final int HOSPITAL_CAPACITY = 81;    // 14,522 beds x 0.36 unoccupied / 64 hospitals = 81.7
    public static final int BASELINE_PATIENTS = 3797;  // 117 deceased + 107 rescued + 960 + 2,613 treated (AFAD)
    public static final double AREA_SIZE = 350;        // coordinates are drawn from [0, 350)
    public static final int INSTANCE_COUNT = 10;       // Table I

    private PECTOInstances() {
    }

    // The patient multiplier of instance 1..10 of Table I: 0.64, 0.72, ..., 1.36
    public static double multiplier(int instance) {
        return multiplierPercent(instance) / 100.0;
    }

    // The number of patients of instance 1..10 of Table I: 2430, 2733, ..., 5163
    public static int patientCount(int instance) {
        return BASELINE_PATIENTS * multiplierPercent(instance) / 100; // rounded down, as in Table I
    }

    // Instance 1..10 of Table I: (200, patientCount(instance), 64)
    public static PECTOProblem paper(int instance, long seed) {
        return random(AMBULANCES, patientCount(instance), HOSPITALS, HOSPITAL_CAPACITY, seed);
    }

    // An instance of any size generated the same way, all hospitals having the given capacity
    public static PECTOProblem random(int ambulances, int patients, int hospitals, int capacity, long seed) {
        Random random = new Random(seed);
        Urgency[] levels = Urgency.values();
        PECTOProblem.Builder builder = PECTOProblem.builder();
        for (int a = 0; a < ambulances; a++)
            builder.ambulance(coordinate(random), coordinate(random));
        for (int p = 0; p < patients; p++)
            builder.patient(coordinate(random), coordinate(random), levels[random.nextInt(levels.length)]);
        for (int h = 0; h < hospitals; h++)
            builder.hospital(coordinate(random), coordinate(random), capacity);
        return builder.build();
    }

    private static double coordinate(Random random) {
        return random.nextDouble() * AREA_SIZE;
    }

    // 64, 72, ..., 136: integers, so that the patient counts are exact
    private static int multiplierPercent(int instance) {
        if (instance < 1 || instance > INSTANCE_COUNT)
            throw new IllegalArgumentException("instance must be between 1 and " + INSTANCE_COUNT);
        return 64 + 8 * (instance - 1);
    }
}
