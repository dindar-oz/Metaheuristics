package experimentrunner;

import lab.model.LabProblem;
import lab.model.ParamSpec;
import lab.model.ParamValues;
import metaheuristic.termination.EvaluationBasedTC;
import metaheuristic.termination.IterationBasedTC;
import metaheuristic.termination.OrCompoundTC;
import metaheuristic.termination.TerminalCondition;
import metaheuristic.termination.TimeBasedTC;
import util.random.Seeds;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * An experiment as submitted (spec.json): the problem and algorithm by their names in the lab's {@code Catalog},
 * parameter values by the lab's parameter names, the instances, the configurations to compare, seeds and budget;
 * and the journal texts that say why it runs and how its results will be read. Example:
 * <pre>
 * {
 *   "id": "pecto-explorer-rate", "title": "Explorer rate on large instances",
 *   "question": "...", "hypothesis": "...", "analysisPlan": "...", "decisionRule": "...",
 *   "problem": "Casualty transport (PECTO)", "problemParams": {"instance seed": 42},
 *   "instances": [{"label": "T8", "seedKey": 8, "params": {"instance": "Table I: 8"}}],
 *   "algorithm": "ACO (MMAS)", "algorithmParams": {},
 *   "configs": [{"label": "plain", "params": {"explorers": 0}}, {"label": "default", "params": {}}],
 *   "baseline": "plain",
 *   "seeds": {"base": 20261016, "firstRepeat": 0, "repeats": 5},
 *   "budget": {"evaluations": 72601},
 *   "traceEvery": 25
 * }
 * </pre>
 * Parameters left out keep the lab's defaults; an instance's params override problemParams, a configuration's override
 * algorithmParams. Repeat r of an instance runs with seed {@code Seeds.derive(base, seedKey, r)} in every
 * configuration, so the configurations are compared on the same random numbers.
 */
public final class ExperimentSpec {

    private static final Pattern ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,79}");

    public String id;
    public String title;
    // the journal: why the experiment runs and how its results will be read
    public String question;
    public String hypothesis;
    public String analysisPlan;
    public String decisionRule;

    public String problem;
    public Map<String, Object> problemParams = new LinkedHashMap<>();
    public List<Instance> instances = new ArrayList<>();
    public String algorithm;
    public Map<String, Object> algorithmParams = new LinkedHashMap<>();
    public List<Config> configs = new ArrayList<>();
    public String baseline;
    public SeedPlan seeds = new SeedPlan();
    public Budget budget = new Budget();
    public int traceEvery;

    public static final class Instance {
        public String label;
        public Long seedKey;            // default: the instance's position
        public Map<String, Object> params = new LinkedHashMap<>();
    }

    public static final class Config {
        public String label;
        public Map<String, Object> params = new LinkedHashMap<>();
    }

    public static final class SeedPlan {
        public long base = 1;
        public int firstRepeat = 0;
        public int repeats = 1;
    }

    // the run stops at the first limit reached
    public static final class Budget {
        public Long evaluations;
        public Long iterations;
        public Long millis;
    }

    // ---- validation against the catalog

    /**
     * Checks the spec against the lab's catalog and fills in the defaults; throws IllegalArgumentException with
     * every problem found.
     */
    public void validate(List<LabProblem> catalog) {
        List<String> errors = new ArrayList<>();
        if (id == null || !ID.matcher(id).matches())
            errors.add("id must be 1-80 letters, digits, '.', '_' or '-', starting with a letter or digit");
        if (title == null || title.isBlank())
            title = id;
        LabProblem p = catalog.stream().filter(x -> x.name().equals(problem)).findFirst().orElse(null);
        if (p == null)
            errors.add("unknown problem '" + problem + "'; known: " + catalog.stream().map(LabProblem::name).toList());
        else if (!p.algorithmNames().contains(algorithm))
            errors.add("unknown algorithm '" + algorithm + "' for " + problem + "; known: " + p.algorithmNames());
        if (instances == null || instances.isEmpty())
            errors.add("instances must not be empty");
        if (configs == null || configs.isEmpty())
            errors.add("configs must not be empty");
        // the parameters can be checked whenever the problem and the algorithm are known
        if (p != null && p.algorithmNames().contains(algorithm) && instances != null && !instances.isEmpty()
                && configs != null && !configs.isEmpty()) {
            check(p.params(), problemParams, "problemParams", errors);
            check(p.algorithmParams(algorithm), algorithmParams, "algorithmParams", errors);
            Set<String> labels = new HashSet<>();
            Set<Long> keys = new HashSet<>();
            for (int i = 0; i < instances.size(); i++) {
                Instance in = instances.get(i);
                if (in.label == null || in.label.isBlank() || in.label.contains(","))
                    errors.add("instance " + i + " needs a label without commas");
                else if (!labels.add(in.label))
                    errors.add("two instances are labelled " + in.label);
                if (in.seedKey == null)
                    in.seedKey = (long) i;
                if (!keys.add(in.seedKey))
                    errors.add("two instances have seedKey " + in.seedKey);
                if (in.params == null)
                    in.params = new LinkedHashMap<>();
                check(p.params(), in.params, "instance " + in.label, errors);
            }
            labels.clear();
            for (Config c : configs) {
                if (c.label == null || c.label.isBlank() || c.label.contains(","))
                    errors.add("every configuration needs a label without commas");
                else if (!labels.add(c.label))
                    errors.add("two configurations are labelled " + c.label);
                if (c.params == null)
                    c.params = new LinkedHashMap<>();
                check(p.algorithmParams(algorithm), c.params, "configuration " + c.label, errors);
            }
            if (baseline != null && !labels.contains(baseline))
                errors.add("baseline '" + baseline + "' is not a configuration label");
        }
        if (seeds == null || seeds.repeats < 1 || seeds.firstRepeat < 0)
            errors.add("seeds.repeats must be at least 1 and seeds.firstRepeat not negative");
        if (budget == null || (positive(budget.evaluations) + positive(budget.iterations) + positive(budget.millis) == 0))
            errors.add("budget needs a positive evaluations, iterations or millis");
        if (traceEvery < 0)
            errors.add("traceEvery must not be negative");
        if (!errors.isEmpty())
            throw new IllegalArgumentException("invalid experiment: " + String.join("; ", errors));
    }

    private static int positive(Long v) {
        return v != null && v > 0 ? 1 : 0;
    }

    private static void check(List<ParamSpec> specs, Map<String, Object> values, String where, List<String> errors) {
        if (values == null)
            return;
        for (Map.Entry<String, Object> e : values.entrySet()) {
            ParamSpec spec = specs.stream().filter(s -> s.name().equals(e.getKey())).findFirst().orElse(null);
            if (spec == null) {
                errors.add(where + ": unknown parameter '" + e.getKey() + "'; known: " + specs.stream().map(ParamSpec::name).toList());
                continue;
            }
            try {
                spec.parse(text(e.getValue()));
            } catch (IllegalArgumentException ex) {
                errors.add(where + ": " + ex.getMessage());
            }
        }
    }

    // JSON numbers arrive as doubles: 5.0 must read as the integer 5
    private static String text(Object value) {
        if (value instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15)
            return Long.toString(d.longValue());
        return String.valueOf(value);
    }

    // ---- what the worker needs

    public LabProblem labProblem(List<LabProblem> catalog) {
        return catalog.stream().filter(x -> x.name().equals(problem)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown problem " + problem));
    }

    public ParamValues problemValues(LabProblem p, Instance instance) {
        return values(p.params(), problemParams, instance.params);
    }

    public ParamValues algorithmValues(LabProblem p, Config config) {
        return values(p.algorithmParams(algorithm), algorithmParams, config.params);
    }

    private static ParamValues values(List<ParamSpec> specs, Map<String, Object> common, Map<String, Object> own) {
        ParamValues v = ParamValues.defaults(specs);
        for (Map<String, Object> m : List.of(common == null ? Map.<String, Object>of() : common, own))
            m.forEach((name, value) -> {
                ParamSpec spec = specs.stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
                v.set(name, spec.parse(text(value)));
            });
        return v;
    }

    public Instance instance(String label) {
        return instances.stream().filter(i -> i.label.equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown instance " + label));
    }

    public Config config(String label) {
        return configs.stream().filter(c -> c.label.equals(label)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown configuration " + label));
    }

    // every run, repeat-major: a partly done experiment has all configurations at the same repeats
    public List<RunKey> runs() {
        List<RunKey> runs = new ArrayList<>();
        for (int r = seeds.firstRepeat; r < seeds.firstRepeat + seeds.repeats; r++)
            for (Instance in : instances)
                for (Config c : configs)
                    runs.add(new RunKey(c.label, in.label, r));
        return runs;
    }

    public long seed(RunKey run) {
        return Seeds.derive(seeds.base, instance(run.instance()).seedKey, run.repeat());
    }

    public TerminalCondition stopCondition() {
        List<TerminalCondition> limits = new ArrayList<>();
        if (positive(budget.evaluations) == 1)
            limits.add(new EvaluationBasedTC(budget.evaluations));
        if (positive(budget.iterations) == 1)
            limits.add(new IterationBasedTC(budget.iterations));
        if (positive(budget.millis) == 1)
            limits.add(new TimeBasedTC(budget.millis));
        return limits.size() == 1 ? limits.get(0) : new OrCompoundTC(limits);
    }

    public String budgetText() {
        List<String> parts = new ArrayList<>();
        if (positive(budget.evaluations) == 1)
            parts.add(String.format("%,d evaluations", budget.evaluations));
        if (positive(budget.iterations) == 1)
            parts.add(String.format("%,d iterations", budget.iterations));
        if (positive(budget.millis) == 1)
            parts.add(String.format("%,d ms", budget.millis));
        return String.join(" or ", parts);
    }
}
