"""Joint analysis of the four-part comparison pecto-hga-vs-aco (Hybrid GA vs PECTOACO).

Implements the analysis plan and decision rule of the hub journal (pecto-hga-vs-aco-1-ga-evals/JOURNAL.md), written
on 2026-10-26 before any result. Pure Python (no SciPy): exact Mann-Whitney U, exact Wilcoxon signed-rank, Holm.
Usage, from the experiments directory:  python -I pecto-hga-vs-aco-1-ga-evals/analyze.py
Writes pecto-hga-vs-aco-1-ga-evals/analysis.md and prints it. Works on partial results (missing data shown as n/a).
"""
import json
import os
import statistics
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
WS = os.path.dirname(HERE)
PARTS = {
    "ga_evals": "pecto-hga-vs-aco-1-ga-evals",
    "aco_evals": "pecto-hga-vs-aco-2-aco-evals",
    "ga_time": "pecto-hga-vs-aco-3-ga-time",
    "aco_time": "pecto-hga-vs-aco-4-aco-time",
    "ga_ablation_181k": "pecto-hga-vs-aco-5-ga-ablation-181k",
    "ga_ablation": "pecto-hga-vs-aco-6-ga-ablation-1m",
}
ABLATION_GROUPS = [
    ("seeding", ["seedRandom"]),
    ("selection", ["selRoulette", "selTournament"]),
    ("crossover", ["xUnbiased", "xRandomRepair", "xUniform", "xNone"]),
    ("mutation", ["mMoveTrip", "mNone", "mFixedRate"]),
    ("stagnation", ["noImmigration", "noPerturbation", "noStagnation"]),
    ("replacement", ["noAgeing"]),
    ("refinement", ["noLS", "noSA", "plain"]),
]
EARLY_CHECKPOINT = 181501
INSTANCES = ["C100", "T1", "T4", "T7", "T10"]
BEDS_USED = {"C100": 100 / 150, "T1": 2430 / 5184, "T4": 3341 / 5184, "T7": 4252 / 5184, "T10": 5163 / 5184}
EVAL_CHECKPOINTS = [10000, 25000, 50000, 100000, 181501]
TIME_CHECKPOINTS_S = [15, 30, 60, 120, 300, 600]
ALPHA = 0.05
MIN_GAP = 0.1  # % ; smaller significant differences count as a tie (decision rule)


# ---------- data

def runs(part):
    """The finished runs of a part from runs.jsonl (always up to date; runs.csv is only written with a summary),
    the final metrics flattened into the row as in runs.csv."""
    path = os.path.join(WS, PARTS[part], "runs.jsonl")
    if not os.path.exists(path):
        return []
    rows = []
    with open(path, encoding="utf-8") as f:
        for line in f:
            if line.strip():
                r = json.loads(line)
                r.update(r.pop("metrics", {}) or {})
                rows.append({k: str(v) for k, v in r.items()})
    return rows


def traces(part):
    path = os.path.join(WS, PARTS[part], "trace.jsonl")
    out = defaultdict(list)  # (config, instance, repeat) -> [lines]
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            for line in f:
                if line.strip():
                    t = json.loads(line)
                    out[(t["config"], t["instance"], int(t["repeat"]))].append(t)
    return out


def costs(rows, config, instance):
    return [float(r["bestCost"]) for r in rows if r["config"] == config and r["instance"] == instance]


# ---------- statistics

def mann_whitney_exact(x, y):
    """Two-sided exact p of the Mann-Whitney U test (no ties expected for continuous costs; ties get mid-ranks,
    which makes the exact p approximate)."""
    n1, n2 = len(x), len(y)
    if n1 == 0 or n2 == 0:
        return float("nan")
    u = sum(1.0 if a < b else 0.5 if a == b else 0.0 for a in x for b in y)  # pairs where x is lower
    # distribution of U under H0: number of ways, via counts[n1][n2][u]
    from functools import lru_cache

    @lru_cache(maxsize=None)
    def count(m, n, k):
        if k < 0:
            return 0
        if m == 0 or n == 0:
            return 1 if k == 0 else 0
        return count(m - 1, n, k - n) + count(m, n - 1, k)

    total = sum(count(n1, n2, k) for k in range(n1 * n2 + 1))
    lo = sum(count(n1, n2, k) for k in range(int(u) + 1)) / total          # P(U <= u)
    hi = sum(count(n1, n2, k) for k in range(int(-(-u // 1)), n1 * n2 + 1)) / total  # P(U >= u)
    return min(1.0, 2 * min(lo, hi))


def wilcoxon_exact(d):
    """Two-sided exact p of the Wilcoxon signed-rank test on paired differences d (zeros dropped)."""
    d = [v for v in d if v != 0]
    n = len(d)
    if n == 0:
        return 1.0
    order = sorted(range(n), key=lambda i: abs(d[i]))
    ranks = [0.0] * n
    i = 0
    while i < n:
        j = i
        while j + 1 < n and abs(d[order[j + 1]]) == abs(d[order[i]]):
            j += 1
        for k in range(i, j + 1):
            ranks[order[k]] = (i + j) / 2 + 1
        i = j + 1
    r2 = [int(round(2 * r)) for r in ranks]  # doubled ranks are integers even with ties
    w = sum(r for r, v in zip(r2, d) if v > 0)
    dist = {0: 1}
    for r in r2:
        nxt = defaultdict(int)
        for s, c in dist.items():
            nxt[s] += c
            nxt[s + r] += c
        dist = nxt
    total = 2 ** n
    lo = sum(c for s, c in dist.items() if s <= w) / total
    hi = sum(c for s, c in dist.items() if s >= w) / total
    return min(1.0, 2 * min(lo, hi))


def holm(ps):
    idx = sorted(range(len(ps)), key=lambda i: (float("inf") if ps[i] != ps[i] else ps[i]))
    out = [float("nan")] * len(ps)
    running = 0.0
    m = sum(1 for p in ps if p == p)
    for rank, i in enumerate(idx):
        if ps[i] != ps[i]:
            continue
        running = max(running, min(1.0, (m - rank) * ps[i]))
        out[i] = running
    return out


def a12(x, y):
    """Probability that a run of x is cheaper than a run of y (ties count half)."""
    if not x or not y:
        return float("nan")
    return sum(1.0 if a < b else 0.5 if a == b else 0.0 for a in x for b in y) / (len(x) * len(y))


def med(v):
    return statistics.median(v) if v else float("nan")


def fmt(v, digits=1):
    if v != v:
        return "n/a"
    return f"{v:,.{digits}f}"


def fmtp(p):
    if p != p:
        return "n/a"
    return "<0.001" if p < 0.001 else f"{p:.3f}"


# ---------- primary: GA default vs ACO default per instance

def compare(ga_rows, aco_rows, title):
    lines = [f"### {title}", "",
             "| instance | beds used | n GA/ACO | median GA | median ACO | gap % (GA-ACO)/ACO | A12 (GA beats ACO) | p | Holm p | winner |",
             "|---|---|---|---|---|---|---|---|---|---|"]
    res = []
    for inst in INSTANCES:
        g, a = costs(ga_rows, "default", inst), costs(aco_rows, "default", inst)
        gap = (med(g) - med(a)) / med(a) * 100 if g and a else float("nan")
        res.append((inst, g, a, gap, a12(g, a), mann_whitney_exact(g, a)))
    hp = holm([r[5] for r in res])
    wins = {"GA": 0, "ACO": 0}
    complete = True
    for (inst, g, a, gap, eff, p), h in zip(res, hp):
        if len(g) < 10 or len(a) < 10:
            complete = False
        winner = "tie"
        if h == h and h < ALPHA and abs(gap) >= MIN_GAP:
            winner = "GA" if gap < 0 else "ACO"
            wins[winner] += 1
        lines.append(f"| {inst} | {BEDS_USED[inst]:.1%} | {len(g)}/{len(a)} | {fmt(med(g))} | {fmt(med(a))} | "
                     f"{fmt(gap, 2)} | {fmt(eff, 2)} | {fmtp(p)} | {fmtp(h)} | {winner} |")
    if wins["GA"] >= 3 and wins["ACO"] == 0:
        verdict = "GA better"
    elif wins["ACO"] >= 3 and wins["GA"] == 0:
        verdict = "PECTOACO better"
    else:
        verdict = "instance-dependent"
    lines += ["", f"**Verdict by the decision rule: {verdict}** (GA wins {wins['GA']}, PECTOACO wins {wins['ACO']}, "
                  f"ties {len(INSTANCES) - wins['GA'] - wins['ACO']})"
              + ("" if complete else " — *provisional: not all 10+10 runs per instance are in*"), ""]
    return lines


# ---------- secondary

def ablation(ga_rows):
    lines = ["### Ablation (part 1): configuration vs GA default, paired by instance and repeat", "",
             "| configuration | pairs | mean gap % vs default | better/worse | p (Wilcoxon) | Holm p | "
             + " | ".join(f"median {i}" for i in INSTANCES) + " |",
             "|---|---|---|---|---|---|" + "---|" * len(INSTANCES)]
    base = {(r["instance"], r["repeat"]): float(r["bestCost"]) for r in ga_rows if r["config"] == "default"}
    rows, ps = [], []
    for cfg in ["noSA", "noLS", "plain"]:
        mine = {(r["instance"], r["repeat"]): float(r["bestCost"]) for r in ga_rows if r["config"] == cfg}
        keys = sorted(set(base) & set(mine))
        gaps = [(mine[k] - base[k]) / base[k] * 100 for k in keys]
        ps.append(wilcoxon_exact(gaps) if gaps else float("nan"))
        rows.append((cfg, keys, gaps))
    for (cfg, keys, gaps), h, p in zip(rows, holm(ps), ps):
        better = sum(1 for v in gaps if v < 0)
        worse = sum(1 for v in gaps if v > 0)
        meds = " | ".join(fmt(med(costs(ga_rows, cfg, i))) for i in INSTANCES)
        lines.append(f"| {cfg} | {len(keys)} | {fmt(statistics.mean(gaps), 3) if gaps else 'n/a'} | {better}/{worse} | "
                     f"{fmtp(p)} | {fmtp(h)} | {meds} |")
    lines += ["", "Positive gap: the configuration is worse than the GA default (the component removed helps).", ""]
    return lines


def plain_vs_aco(ga_rows, aco_rows):
    lines = ["### Plain GA (no local search, no SA) vs PECTOACO, equal evaluations", "",
             "| instance | median plain GA | median ACO | gap % | A12 | p |", "|---|---|---|---|---|---|"]
    for inst in INSTANCES:
        g, a = costs(ga_rows, "plain", inst), costs(aco_rows, "default", inst)
        gap = (med(g) - med(a)) / med(a) * 100 if g and a else float("nan")
        lines.append(f"| {inst} | {fmt(med(g))} | {fmt(med(a))} | {fmt(gap, 2)} | {fmt(a12(g, a), 2)} | "
                     f"{fmtp(mann_whitney_exact(g, a))} |")
    return lines + [""]


def best_at(trace_lines, key, checkpoint):
    """Best cost of the last trace line at or before the checkpoint (None if the run had not reached it)."""
    best = None
    for t in trace_lines:
        x = t["evaluations"] if key == "evaluations" else t["elapsedMs"] / 1000
        if x <= checkpoint:
            best = t["bestCost"]
    return best


def anytime(ga_part, aco_part, key, checkpoints, final_rows):
    unit = "evaluations" if key == "evaluations" else "s"
    lines = [f"### Anytime: median best cost by {unit} ({PARTS[ga_part]} vs {PARTS[aco_part]})", "",
             "| instance | algorithm | " + " | ".join(f"{c:,} {unit}" for c in checkpoints) + " |",
             "|---|---|" + "---|" * len(checkpoints)]
    for inst in INSTANCES:
        for label, part in [("GA", ga_part), ("ACO", aco_part)]:
            tr = traces(part)
            finals = {int(r["repeat"]): r for r in final_rows[part] if r["config"] == "default" and r["instance"] == inst}
            cells = []
            for c in checkpoints:
                vals = []
                for rep, r in finals.items():
                    end = float(r["evaluations"]) if key == "evaluations" else float(r["elapsedMs"]) / 1000
                    if c >= end:  # the run ended before or at the checkpoint: its final best
                        vals.append(float(r["bestCost"]))
                    else:
                        v = best_at(tr.get(("default", inst, rep), []), key, c)
                        if v is not None:
                            vals.append(v)
                cells.append(f"{fmt(med(vals))} ({len(vals)})" if vals else "n/a")
            lines.append(f"| {inst} | {label} | " + " | ".join(cells) + " |")
    return lines + ["", "(n) runs with a value at the checkpoint; a run that ended earlier counts with its final best.", ""]


def descriptive(final_rows):
    lines = ["### Final diversity, duplicates and throughput (descriptive; diversity measures different sets, see Notes)", "",
             "| part | instance | median diversity | median duplicates | median evaluations | median iterations | median eval/s |",
             "|---|---|---|---|---|---|---|"]
    for part in PARTS:
        for inst in INSTANCES:
            rs = [r for r in final_rows[part] if r["config"] == "default" and r["instance"] == inst]
            if not rs:
                continue
            g = lambda k: med([float(r[k]) for r in rs if r.get(k) not in (None, "")])
            eps = med([float(r["evaluations"]) / max(1.0, float(r["elapsedMs"]) / 1000) for r in rs])
            lines.append(f"| {PARTS[part][-9:]} | {inst} | {fmt(g('diversity'), 4)} | {fmt(g('duplicates'), 3)} | "
                         f"{fmt(g('evaluations'), 0)} | {fmt(g('iterations'), 0)} | {fmt(eps, 0)} |")
    return lines + [""]


def load_check(final_rows):
    lines = ["### Load check, time parts: evaluations per second in finishing order (runs.jsonl order)", ""]
    for part in ["ga_time", "aco_time"]:
        for inst in INSTANCES:
            rs = [r for r in final_rows[part] if r["instance"] == inst]
            if rs:
                eps = [float(r["evaluations"]) / max(1.0, float(r["elapsedMs"]) / 1000) for r in rs]
                lines.append(f"- {PARTS[part]} {inst}: " + ", ".join(f"{v:,.0f}" for v in eps))
    return lines + [""]


def t975(df):
    """97.5 % quantile of Student's t (Cornish-Fisher expansion; within 0.001 of the exact value for df >= 5)."""
    z = 1.959963985
    return (z + (z ** 3 + z) / (4 * df) + (5 * z ** 5 + 16 * z ** 3 + 3 * z) / (96 * df ** 2)
            + (3 * z ** 7 + 19 * z ** 5 + 17 * z ** 3 - 15 * z) / (384 * df ** 3))


def paired_gaps(values, cfg):
    """values: (config, instance, repeat) -> cost. Paired % gaps of cfg to default, with their (instance, repeat)."""
    out = []
    for (c, inst, rep), v in values.items():
        if c == cfg and ("default", inst, rep) in values:
            d = values[("default", inst, rep)]
            out.append((inst, rep, (v - d) / d * 100))
    return out


def label(gaps, holm_p):
    n = len(gaps)
    if n < 2:
        return "n/a", float("nan"), float("nan")
    m = statistics.mean(gaps)
    half = t975(n - 1) * statistics.stdev(gaps) / n ** 0.5
    if holm_p == holm_p and holm_p < ALPHA and m >= MIN_GAP:
        verdict = "ESSENTIAL"
    elif holm_p == holm_p and holm_p < ALPHA and m <= -MIN_GAP:
        verdict = "HARMFUL"
    elif m + half < MIN_GAP:
        verdict = "REMOVABLE"
    else:
        verdict = "INCONCLUSIVE"
    return verdict, m - half, m + half


def ablation_table(values, title, configs):
    lines = [f"### {title}", "",
             "| group | variant | pairs | mean gap % | 95 % interval | better/worse | p | Holm p | verdict |",
             "|---|---|---|---|---|---|---|---|---|"]
    gaps = {c: paired_gaps(values, c) for c in configs}
    ps = [wilcoxon_exact([g for _, _, g in gaps[c]]) if gaps[c] else float("nan") for c in configs]
    hp = dict(zip(configs, holm(ps)))
    praw = dict(zip(configs, ps))
    complete = True
    for group, members in ABLATION_GROUPS:
        for c in members:
            g = [x for _, _, x in gaps[c]]
            if len(g) < 50:
                complete = False
            verdict, lo, hi = label(g, hp[c])
            lines.append(f"| {group} | {c} | {len(g)} | {fmt(statistics.mean(g), 3) if g else 'n/a'} | "
                         f"{fmt(lo, 3)} … {fmt(hi, 3)} | {sum(1 for x in g if x < 0)}/{sum(1 for x in g if x > 0)} | "
                         f"{fmtp(praw[c])} | {fmtp(hp[c])} | {verdict} |")
    lines += ["", "Gap: (variant - default) / default of the run with the same instance and repeat; positive means "
                  "the variant is worse, i.e. the removed mechanism helps. Verdicts by the decision rule of part 5."
              + ("" if complete else " *Provisional: not all 50 pairs are in.*"), ""]
    return lines, gaps


def ablation5(final_rows):
    rows = final_rows["ga_ablation"]
    short = final_rows["ga_ablation_181k"]
    out = ["## Parts 5 and 6: ablation of every mechanism", ""]
    if not rows and not short:
        return out + ["No runs yet.", ""]
    configs = [c for _, members in ABLATION_GROUPS for c in members]
    final = {(r["config"], r["instance"], int(r["repeat"])): float(r["bestCost"]) for r in rows}
    early = {(r["config"], r["instance"], int(r["repeat"])): float(r["bestCost"]) for r in short}
    t1, gaps = ablation_table(final, "Part 6: final best cost at 1,000,000 evaluations (decides)", configs)
    t2, _ = ablation_table(early, f"Part 5: final best cost at {EARLY_CHECKPOINT:,} evaluations (reported only)", configs)
    out += t1 + t2
    out += ["### Mean gap % to default by instance (part 6, 1M evaluations)", "",
            "| variant | " + " | ".join(f"{i} ({BEDS_USED[i]:.0%})" for i in INSTANCES) + " |",
            "|---|" + "---|" * len(INSTANCES)]
    for c in configs:
        cells = []
        for inst in INSTANCES:
            g = [x for i, _, x in gaps[c] if i == inst]
            cells.append(fmt(statistics.mean(g), 3) if g else "n/a")
        out.append(f"| {c} | " + " | ".join(cells) + " |")
    out += ["", "### Activity at the end of part 6 (medians over all instances)", "",
            "| configuration | immigrations | mutation rate | duplicates | diversity | stagnation | eval/s |",
            "|---|---|---|---|---|---|---|"]
    for c in ["default"] + configs:
        rs = [r for r in rows if r["config"] == c]
        g = lambda k: med([float(r[k]) for r in rs if r.get(k) not in (None, "")])
        eps = med([float(r["evaluations"]) / max(1.0, float(r["elapsedMs"]) / 1000) for r in rs]) if rs else float("nan")
        out.append(f"| {c} | {fmt(g('immigrations'), 0)} | {fmt(g('mutationRate'), 3)} | {fmt(g('duplicates'), 3)} | "
                   f"{fmt(g('diversity'), 4)} | {fmt(g('stagnation'), 0)} | {fmt(eps, 0)} |")
    # replication check: part 5's default, noSA, noLS and plain repeat part 1's runs (same seed, budget and code path)
    p1 = {(r["config"], r["instance"], int(r["repeat"])): float(r["bestCost"]) for r in final_rows["ga_evals"]}
    common = [k for k in early if k in p1]
    differing = [k for k in common if early[k] != p1[k]]
    out += ["", f"Replication check (part 5 vs part 1, same configuration, seed and budget): {len(common)} runs "
                f"compared, {len(differing)} differ (0 expected)" + (": " + ", ".join(map(str, differing[:10])) if differing else "."), ""]
    return out


def main():
    final_rows = {p: runs(p) for p in PARTS}
    out = ["# Joint analysis: Hybrid GA vs PECTOACO", "",
           "_Generated by analyze.py from the runs.jsonl and trace.jsonl of the four parts. Runs per part: "
           + ", ".join(f"{PARTS[p]} {len(final_rows[p])}" for p in PARTS) + "._", "",
           "## Primary (decision rule)", ""]
    out += compare(final_rows["ga_evals"], final_rows["aco_evals"], "Equal evaluations (181,501): parts 1 + 2")
    out += compare(final_rows["ga_time"], final_rows["aco_time"], "Equal wall time (600 s): parts 3 + 4")
    out += ["Gap: (median GA - median ACO) / median ACO; negative means the GA is cheaper. p: exact two-sided "
            "Mann-Whitney U on the final best costs; Holm over the 5 instances. Winner: Holm p < 0.05 and |gap| >= 0.1 %.", "",
            "## Secondary", ""]
    out += ablation(final_rows["ga_evals"])
    out += plain_vs_aco(final_rows["ga_evals"], final_rows["aco_evals"])
    out += anytime("ga_evals", "aco_evals", "evaluations", EVAL_CHECKPOINTS, final_rows)
    out += anytime("ga_time", "aco_time", "time", TIME_CHECKPOINTS_S, final_rows)
    out += descriptive(final_rows)
    out += load_check(final_rows)
    out += ablation5(final_rows)
    text = "\n".join(out)
    with open(os.path.join(HERE, "analysis.md"), "w", encoding="utf-8") as f:
        f.write(text)
    sys.stdout.reconfigure(encoding="utf-8")
    print(text)


if __name__ == "__main__":
    main()
