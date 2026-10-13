package problems.wflop;

/**
 * The evaluation of one layout, with the indicators reported by Yu &amp; Zhang (2024) in Tables 6-9.
 * Immutable.
 */
public final class LayoutEvaluation {

    private final WindFarm farm;
    private final int[] cells;
    private final double power;
    private final double cost;

    LayoutEvaluation(WindFarm farm, int[] cells, double power, double cost) {
        this.farm = farm;
        this.cells = cells;
        this.power = power;
        this.cost = cost;
    }

    // occupied cells, ascending
    public int[] cells() {
        return cells.clone();
    }

    public int turbines() {
        return cells.length;
    }

    // expected total power [kW], Eq. (9)
    public double power() {
        return power;
    }

    // dimensionless annual cost, Eq. (8)
    public double cost() {
        return cost;
    }

    // cost / power, Eq. (7); +infinity for an empty layout
    public double objective() {
        return cells.length == 0 ? Double.POSITIVE_INFINITY : cost / power;
    }

    // Eq. (10): power / (N * 0.3 * v0^3), the "efficiency" reported by the paper (uses Mosetti's rated power curve)
    public double efficiency() {
        return cells.length == 0 ? 0 : power / farm.ratedPower(cells.length);
    }

    // power / power without wakes: the fraction of energy not lost to wakes
    public double wakeEfficiency() {
        return cells.length == 0 ? 0 : power / farm.wakeFreePower(cells.length);
    }

    // The layout as text, north up: 'X' = turbine, '.' = empty cell
    public String render() {
        int side = farm.cellsPerSide();
        boolean[] occupied = new boolean[farm.cellCount()];
        for (int c : cells)
            occupied[c] = true;
        StringBuilder sb = new StringBuilder();
        for (int row = side - 1; row >= 0; row--) {
            for (int column = 0; column < side; column++)
                sb.append(occupied[row * side + column] ? " X" : " .");
            sb.append('\n');
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("N=%d power=%.4f kW cost=%.4f objective=%.7f efficiency=%.2f%%",
                turbines(), power, cost, objective(), 100 * efficiency());
    }
}
