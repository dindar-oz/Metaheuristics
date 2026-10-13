package problems.wflop;

/**
 * The four wind farm cases of Yu &amp; Zhang (2024), Table 1 (taken from Kunakote et al. 2022).
 * Each can be combined with Wind Condition 1 or 2 ({@link WindScenario}).
 */
public enum WFLOPCase {

    CASE_1("Case 1: 1-100 turbines, full wake", WakeMode.FULL, 0),
    CASE_2("Case 2: 1-100 turbines, partial wake", WakeMode.PARTIAL, 0),
    CASE_3("Case 3: 39 turbines, full wake", WakeMode.FULL, 39),
    CASE_4("Case 4: 39 turbines, partial wake", WakeMode.PARTIAL, 39);

    private final String description;
    private final WakeMode wakeMode;
    private final int turbines; // 0 = variable

    WFLOPCase(String description, WakeMode wakeMode, int turbines) {
        this.description = description;
        this.wakeMode = wakeMode;
        this.turbines = turbines;
    }

    public WakeMode wakeMode() {
        return wakeMode;
    }

    public boolean hasFixedTurbineCount() {
        return turbines > 0;
    }

    // The problem with the paper's farm parameters (Table 5) and turbine count
    public WFLOPProblem create(WindScenario wind) {
        return create(wind, turbines);
    }

    // For Cases 3 and 4 the number of turbines can be changed (ignored for Cases 1 and 2)
    public WFLOPProblem create(WindScenario wind, int fixedTurbines) {
        WindFarm farm = new WindFarm(WindFarmParameters.paper(), wakeMode, wind);
        return hasFixedTurbineCount() ? new FixedTurbineWFLOP(farm, fixedTurbines) : new VariableTurbineWFLOP(farm);
    }

    // The same case over layouts (sets of cells) instead of the paper's real-vector encodings
    public DiscreteWFLOP createDiscrete(WindScenario wind) {
        return createDiscrete(wind, turbines);
    }

    public DiscreteWFLOP createDiscrete(WindScenario wind, int fixedTurbines) {
        return createDiscrete(WindFarmParameters.paper(), wind, fixedTurbines);
    }

    // The same case on a farm with other parameters, e.g. a larger one (fixedTurbines is ignored for Cases 1 and 2)
    public DiscreteWFLOP createDiscrete(WindFarmParameters parameters, WindScenario wind, int fixedTurbines) {
        WindFarm farm = new WindFarm(parameters, wakeMode, wind);
        return hasFixedTurbineCount() ? DiscreteWFLOP.fixed(farm, fixedTurbines) : DiscreteWFLOP.variable(farm);
    }

    @Override
    public String toString() {
        return description;
    }
}
