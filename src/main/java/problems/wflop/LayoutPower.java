package problems.wflop;

/**
 * The power of a layout, broken down by turbine and wind state. {@link CellLayout} keeps it so that a layout derived
 * from it by a few changes can be evaluated incrementally (see {@link WindFarm#power(CellLayout)}).
 * <p>
 * Terms are fixed-point integers: integer sums are exact and do not depend on the order of the additions, so a total
 * updated by the changes of a few terms equals the total of a full evaluation bit for bit. Immutable.
 */
final class LayoutPower {

    // fixed-point scale of the terms: probability * v^3 <= 17^3 m^3/s^3 is kept to 2^-32 (~2e-10, relative ~1e-13)
    static final double SCALE = 0x1p32;

    final WindFarm farm;
    final int[] cells;     // the layout's cells, ascending (in the given order for unsorted layouts)
    final long[] terms;    // [k * states + s]: probability * cubed speed at turbine cells[k] in state s, * SCALE
    final long total;      // the sum of the terms
    final double power;    // power coefficient * total / SCALE [kW]

    LayoutPower(WindFarm farm, int[] cells, long[] terms, long total, double powerCoefficient) {
        this.farm = farm;
        this.cells = cells;
        this.terms = terms;
        this.total = total;
        this.power = powerCoefficient * (total / SCALE);
    }

    static long fixed(double term) {
        return (long) (term * SCALE + 0.5);
    }
}
