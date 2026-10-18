package experimentrunner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The results of an experiment, as far as its runs have finished: per configuration the best costs and the mean final
 * metrics, and with a baseline the paired gap to it (the same instance and repeat, so the same seed): mean, 95% t
 * interval, better/worse counts, exact two-sided Wilcoxon signed-rank p and Holm-adjusted p. Written to summary.md,
 * summary.json and runs.csv, and into the results block of the journal.
 */
public final class Summary {

    public String experiment;
    public int runs;
    public int total;
    public String baseline;
    public List<Row> rows = new ArrayList<>();

    public static final class Row {
        public String config;
        public int n;
        public double meanCost, medianCost, sdCost, minCost;
        public Map<String, Double> perInstanceCost = new LinkedHashMap<>();
        public Map<String, Double> metrics = new LinkedHashMap<>();    // means of the final metrics
        // against the baseline (null without one)
        public Integer pairs;
        public Double gap, gapLow, gapHigh;                             // mean gap in %, 95% interval
        public Integer better, worse;
        public Double p, pHolm;
        public Map<String, Double> perInstanceGap = new LinkedHashMap<>();
    }

    public static Summary of(ExperimentSpec spec, List<RunRecord> all) {
        // a run recorded twice (it can happen after a crash) counts once
        Map<RunKey, RunRecord> byKey = new LinkedHashMap<>();
        for (RunRecord r : all)
            byKey.putIfAbsent(r.key(), r);
        Summary s = new Summary();
        s.experiment = spec.id;
        s.runs = byKey.size();
        s.total = spec.runs().size();
        s.baseline = spec.baseline;
        Map<String, Double> base = new HashMap<>();     // instance,repeat -> baseline cost
        if (spec.baseline != null)
            for (RunRecord r : byKey.values())
                if (r.config.equals(spec.baseline))
                    base.put(r.instance + "," + r.repeat, r.bestCost);

        for (ExperimentSpec.Config c : spec.configs) {
            List<RunRecord> rs = byKey.values().stream().filter(r -> r.config.equals(c.label)).toList();
            if (rs.isEmpty())
                continue;
            Row row = new Row();
            row.config = c.label;
            row.n = rs.size();
            double[] costs = rs.stream().mapToDouble(r -> r.bestCost).toArray();
            row.meanCost = mean(costs);
            row.medianCost = median(costs);
            row.sdCost = sd(costs);
            row.minCost = java.util.Arrays.stream(costs).min().orElse(Double.NaN);
            for (ExperimentSpec.Instance in : spec.instances) {
                double[] ci = rs.stream().filter(r -> r.instance.equals(in.label)).mapToDouble(r -> r.bestCost).toArray();
                if (ci.length > 0)
                    row.perInstanceCost.put(in.label, mean(ci));
            }
            Set<String> names = new TreeSet<>();
            rs.forEach(r -> names.addAll(r.metrics.keySet()));
            for (String m : names) {
                double[] v = rs.stream().filter(r -> r.metrics.get(m) != null && !r.metrics.get(m).isNaN())
                        .mapToDouble(r -> r.metrics.get(m)).toArray();
                if (v.length > 0)
                    row.metrics.put(m, mean(v));
            }
            if (spec.baseline != null) {
                List<Double> gaps = new ArrayList<>();
                Map<String, List<Double>> byInstance = new LinkedHashMap<>();
                for (RunRecord r : rs) {
                    Double b = base.get(r.instance + "," + r.repeat);
                    if (b == null || b == 0)
                        continue;
                    double g = 100 * (r.bestCost - b) / Math.abs(b);
                    gaps.add(g);
                    byInstance.computeIfAbsent(r.instance, k -> new ArrayList<>()).add(g);
                }
                double[] g = gaps.stream().mapToDouble(Double::doubleValue).toArray();
                row.pairs = g.length;
                if (g.length > 0) {
                    row.gap = mean(g);
                    double h = g.length > 1 ? tQuantile975(g.length - 1) * sd(g) / Math.sqrt(g.length) : 0;
                    row.gapLow = row.gap - h;
                    row.gapHigh = row.gap + h;
                    row.better = (int) java.util.Arrays.stream(g).filter(x -> x < 0).count();
                    row.worse = (int) java.util.Arrays.stream(g).filter(x -> x > 0).count();
                    row.p = c.label.equals(spec.baseline) ? 1.0 : wilcoxon(g);
                    byInstance.forEach((in, v) -> row.perInstanceGap.put(in, mean(v.stream().mapToDouble(Double::doubleValue).toArray())));
                }
            }
            s.rows.add(row);
        }
        // Holm over the configurations compared with the baseline
        List<Row> tested = s.rows.stream().filter(r -> r.p != null && !r.config.equals(spec.baseline)).toList();
        double[] adjusted = holm(tested.stream().mapToDouble(r -> r.p).toArray());
        for (int i = 0; i < tested.size(); i++)
            tested.get(i).pHolm = adjusted[i];
        s.rows.forEach(r -> {
            if (r.config.equals(spec.baseline) && r.p != null)
                r.pHolm = 1.0;
        });
        s.rows.sort(Comparator.comparingDouble((Row r) -> r.gap != null ? r.gap : r.meanCost));
        return s;
    }

    // ---- output

    // summary.md, summary.json, runs.csv and the journal's results block
    public static Summary write(Workspace ws, String id) {
        ExperimentSpec spec = ws.spec(id);
        List<RunRecord> runs = ws.runs(id);
        Summary s = of(spec, runs);
        String md = s.markdown(spec);
        Json.writeString(ws.summaryFile(id), md);
        Json.write(ws.dir(id).resolve("summary.json"), s);
        Json.writeString(ws.dir(id).resolve("runs.csv"), csv(runs));
        Journal.writeResults(ws, id, md);
        return s;
    }

    public String markdown(ExperimentSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "%d of %d runs finished%s.%n%n", runs, total, runs < total ? " (partial)" : ""));
        Set<String> metricNames = new LinkedHashSet<>();
        rows.forEach(r -> metricNames.addAll(r.metrics.keySet()));
        metricNames.removeIf(m -> m.equals("currentCost") || m.equals("meanCost") || m.equals("stagnation"));
        boolean paired = baseline != null;
        sb.append("| configuration | n | mean cost | median |");
        if (paired)
            sb.append(" gap to ").append(baseline).append(" % | 95% interval | better/worse | p | Holm p |");
        for (String m : metricNames)
            sb.append(' ').append(m).append(" |");
        sb.append("\n|---|---|---|---|");
        if (paired)
            sb.append("---|---|---|---|---|");
        sb.append("---|".repeat(metricNames.size())).append('\n');
        for (Row r : rows) {
            sb.append(String.format(Locale.ROOT, "| %s | %d | %.4f | %.4f |", r.config, r.n, r.meanCost, r.medianCost));
            if (paired)
                sb.append(r.gap == null ? " | | | | |" : String.format(Locale.ROOT, " %+.3f | %+.3f … %+.3f | %d/%d | %s | %s |",
                        r.gap, r.gapLow, r.gapHigh, r.better, r.worse, p(r.p), p(r.pHolm)));
            for (String m : metricNames)
                sb.append(r.metrics.containsKey(m) ? String.format(Locale.ROOT, " %.4g |", r.metrics.get(m)) : " |");
            sb.append('\n');
        }
        if (paired) {
            sb.append("\nGap to ").append(baseline).append(" by instance (%):\n\n| configuration |");
            spec.instances.forEach(in -> sb.append(' ').append(in.label).append(" |"));
            sb.append("\n|---|").append("---|".repeat(spec.instances.size())).append('\n');
            for (Row r : rows) {
                sb.append("| ").append(r.config).append(" |");
                spec.instances.forEach(in -> {
                    Double g = r.perInstanceGap.get(in.label);
                    sb.append(g == null ? " |" : String.format(Locale.ROOT, " %+.3f |", g));
                });
                sb.append('\n');
            }
            sb.append("\nGap: (cost − ").append(baseline).append(" cost) / ").append(baseline)
                    .append(" cost of the run with the same instance and repeat; negative is better. p: exact two-sided ")
                    .append("Wilcoxon signed-rank test of the gaps; Holm p: adjusted over the configurations.\n");
        }
        return sb.toString();
    }

    private static String p(Double p) {
        if (p == null)
            return "";
        return p < 0.001 ? "<0.001" : String.format(Locale.ROOT, p < 0.01 ? "%.4f" : "%.3f", p);
    }

    static String csv(List<RunRecord> runs) {
        Set<String> metrics = new TreeSet<>();
        runs.forEach(r -> metrics.addAll(r.metrics.keySet()));
        StringBuilder sb = new StringBuilder("config,instance,repeat,seed,bestCost,evaluations,iterations,elapsedMs,"
                + "timeToBestMs,stopReason,runtime");
        metrics.forEach(m -> sb.append(',').append(m));
        sb.append('\n');
        for (RunRecord r : runs) {
            sb.append(String.format(Locale.ROOT, "%s,%s,%d,%d,%s,%d,%d,%d,%d,%s,%s", r.config, r.instance, r.repeat,
                    r.seed, r.bestCost, r.evaluations, r.iterations, r.elapsedMs, r.timeToBestMs, r.stopReason, r.runtime));
            metrics.forEach(m -> sb.append(',').append(r.metrics.containsKey(m) ? r.metrics.get(m) : ""));
            sb.append('\n');
        }
        return sb.toString();
    }

    // ---- statistics

    static double mean(double[] x) {
        double s = 0;
        for (double v : x)
            s += v;
        return x.length == 0 ? Double.NaN : s / x.length;
    }

    static double sd(double[] x) {
        if (x.length < 2)
            return 0;
        double m = mean(x), s = 0;
        for (double v : x)
            s += (v - m) * (v - m);
        return Math.sqrt(s / (x.length - 1));
    }

    static double median(double[] x) {
        double[] y = x.clone();
        java.util.Arrays.sort(y);
        int n = y.length;
        return n == 0 ? Double.NaN : n % 2 == 1 ? y[n / 2] : (y[n / 2 - 1] + y[n / 2]) / 2;
    }

    // the 97.5% quantile of Student's t with df degrees of freedom
    static double tQuantile975(int df) {
        return new org.apache.commons.math3.distribution.TDistribution(df).inverseCumulativeProbability(0.975);
    }

    /**
     * Exact two-sided Wilcoxon signed-rank p of the differences; zeros are dropped and ties get average ranks. The
     * null distribution is computed on doubled ranks; above 400 nonzero differences the normal approximation is used.
     */
    static double wilcoxon(double[] diffs) {
        double[] d = java.util.Arrays.stream(diffs).filter(x -> x != 0).toArray();
        int n = d.length;
        if (n == 0)
            return 1;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++)
            order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble(i -> Math.abs(d[i])));
        int[] rank2 = new int[n];               // doubled ranks, integers even with ties
        for (int i = 0; i < n; ) {
            int j = i;
            while (j + 1 < n && Math.abs(d[order[j + 1]]) == Math.abs(d[order[i]]))
                j++;
            for (int k = i; k <= j; k++)
                rank2[order[k]] = i + j + 2;
            i = j + 1;
        }
        long wPlus = 0, total = 0;
        for (int i = 0; i < n; i++) {
            total += rank2[i];
            if (d[i] > 0)
                wPlus += rank2[i];
        }
        if (n > 400) {
            double mean = total / 4.0, var = 0;
            for (int r : rank2)
                var += r * (double) r / 4.0;
            double z = (wPlus - mean) / Math.sqrt(var);
            return Math.min(1, 2 * (1 - new org.apache.commons.math3.distribution.NormalDistribution().cumulativeProbability(Math.abs(z))));
        }
        double[] prob = new double[(int) total + 1];  // P(W+ = s) under the null, built one rank at a time
        prob[0] = 1;
        int reach = 0;
        for (int r : rank2) {
            for (int s = reach; s >= 0; s--)
                if (prob[s] != 0) {
                    prob[s + r] += prob[s] / 2;
                    prob[s] /= 2;
                }
            reach += r;
        }
        double low = 0, high = 0;
        for (int s = 0; s <= total; s++) {
            if (s <= wPlus)
                low += prob[s];
            if (s >= wPlus)
                high += prob[s];
        }
        return Math.min(1, 2 * Math.min(low, high));
    }

    static double[] holm(double[] p) {
        Integer[] order = new Integer[p.length];
        for (int i = 0; i < p.length; i++)
            order[i] = i;
        java.util.Arrays.sort(order, Comparator.comparingDouble(i -> p[i]));
        double[] adjusted = new double[p.length];
        double running = 0;
        for (int k = 0; k < p.length; k++) {
            running = Math.max(running, Math.min(1, (p.length - k) * p[order[k]]));
            adjusted[order[k]] = running;
        }
        return adjusted;
    }

    static void ensureDir(java.nio.file.Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String read(java.nio.file.Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
