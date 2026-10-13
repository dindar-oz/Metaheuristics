package problems.wflop;

/**
 * Site and turbine parameters of the wind farm (Yu &amp; Zhang 2024, Table 5, which follows Mosetti et al. 1994).
 * Immutable.
 */
public final class WindFarmParameters {

    private final double hubHeight;          // h [m]
    private final double rotorRadius;        // r [m]
    private final double thrustCoefficient;  // C_T
    private final double turbineEfficiency;  // eta
    private final double airDensity;         // rho [kg/m^3]
    private final double surfaceRoughness;   // z0 [m]
    private final double farmSize;           // side of the square farm [m]
    private final double cellSize;           // side of a grid cell [m]
    private final double ratedPowerFactor;   // Mosetti's rated power curve P = factor * v^3 [kW], used by Eq. (10)

    private WindFarmParameters(Builder b) {
        this.hubHeight = positive("hubHeight", b.hubHeight);
        this.rotorRadius = positive("rotorRadius", b.rotorRadius);
        this.thrustCoefficient = b.thrustCoefficient;
        if (!(thrustCoefficient > 0 && thrustCoefficient < 1))
            throw new IllegalArgumentException("thrustCoefficient must be in (0, 1)");
        this.turbineEfficiency = positive("turbineEfficiency", b.turbineEfficiency);
        this.airDensity = positive("airDensity", b.airDensity);
        this.surfaceRoughness = positive("surfaceRoughness", b.surfaceRoughness);
        this.farmSize = positive("farmSize", b.farmSize);
        this.cellSize = positive("cellSize", b.cellSize);
        this.ratedPowerFactor = positive("ratedPowerFactor", b.ratedPowerFactor);
        if (hubHeight <= surfaceRoughness)
            throw new IllegalArgumentException("hubHeight must be above the surface roughness");
        if (farmSize % cellSize != 0)
            throw new IllegalArgumentException("farmSize must be a multiple of cellSize");
    }

    // The values of Table 5: h=60 m, r=20 m, C_T=0.88, eta=40%, rho=1.225, z0=0.3 m, 2 km x 2 km farm, 200 m cells
    public static WindFarmParameters paper() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    // Wake decay (entrainment) constant alpha = 0.5 / ln(h / z0) (Mosetti et al. 1994)
    public double wakeDecay() {
        return 0.5 / Math.log(hubHeight / surfaceRoughness);
    }

    // Initial velocity deficit a = 1 - sqrt(1 - C_T), Eq. (2)
    public double initialDeficit() {
        return 1 - Math.sqrt(1 - thrustCoefficient);
    }

    public double rotorArea() {
        return Math.PI * rotorRadius * rotorRadius;
    }

    // kW produced per (m/s)^3 of wind speed at the rotor: 1/2 eta rho A / 1000, Eq. (9)
    public double powerCoefficient() {
        return 0.5 * turbineEfficiency * airDensity * rotorArea() / 1000.0;
    }

    public int cellsPerSide() {
        return (int) Math.round(farmSize / cellSize);
    }

    public double hubHeight() {
        return hubHeight;
    }

    public double rotorRadius() {
        return rotorRadius;
    }

    public double thrustCoefficient() {
        return thrustCoefficient;
    }

    public double turbineEfficiency() {
        return turbineEfficiency;
    }

    public double airDensity() {
        return airDensity;
    }

    public double surfaceRoughness() {
        return surfaceRoughness;
    }

    public double farmSize() {
        return farmSize;
    }

    public double cellSize() {
        return cellSize;
    }

    public double ratedPowerFactor() {
        return ratedPowerFactor;
    }

    private static double positive(String name, double value) {
        if (!(value > 0) || Double.isInfinite(value))
            throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    @Override
    public String toString() {
        return String.format("h=%.1fm r=%.1fm CT=%.2f eta=%.2f rho=%.3f z0=%.2fm farm=%.0fm cell=%.0fm",
                hubHeight, rotorRadius, thrustCoefficient, turbineEfficiency, airDensity, surfaceRoughness, farmSize, cellSize);
    }

    public static final class Builder {
        private double hubHeight = 60;
        private double rotorRadius = 20;
        private double thrustCoefficient = 0.88;
        private double turbineEfficiency = 0.40;
        private double airDensity = 1.225;
        private double surfaceRoughness = 0.3;
        private double farmSize = 2000;
        private double cellSize = 200;
        private double ratedPowerFactor = 0.3;

        private Builder() {
        }

        public Builder hubHeight(double v) {
            hubHeight = v;
            return this;
        }

        public Builder rotorRadius(double v) {
            rotorRadius = v;
            return this;
        }

        public Builder thrustCoefficient(double v) {
            thrustCoefficient = v;
            return this;
        }

        public Builder turbineEfficiency(double v) {
            turbineEfficiency = v;
            return this;
        }

        public Builder airDensity(double v) {
            airDensity = v;
            return this;
        }

        public Builder surfaceRoughness(double v) {
            surfaceRoughness = v;
            return this;
        }

        public Builder farmSize(double v) {
            farmSize = v;
            return this;
        }

        public Builder cellSize(double v) {
            cellSize = v;
            return this;
        }

        public Builder ratedPowerFactor(double v) {
            ratedPowerFactor = v;
            return this;
        }

        public WindFarmParameters build() {
            return new WindFarmParameters(this);
        }
    }
}
