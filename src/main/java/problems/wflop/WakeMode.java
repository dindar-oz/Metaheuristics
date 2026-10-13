package problems.wflop;

/**
 * How a wake that reaches a downstream rotor is accounted for (Yu &amp; Zhang 2024, Figs. 7 and 8, Table 1).
 */
public enum WakeMode {

    /**
     * Location 1 (Cases 1 and 3): the full single-wake deficit a (r / r_x)^2 applies whenever the wake circle
     * reaches the downstream rotor, i.e. when the lateral distance is less than r_x + r.
     * (The text says "the center of the downstream turbine is within the wake"; the overlap criterion is the one
     * that reproduces the powers reported in the paper exactly.)
     */
    FULL,

    /**
     * Location 2 (Cases 2 and 4): the deficit is scaled by the fraction of the rotor disk covered by the wake,
     * a (r / r_x)^2 A_ij / A_0, Eqs. (5)-(6) (Katic et al. 1987).
     */
    PARTIAL
}
