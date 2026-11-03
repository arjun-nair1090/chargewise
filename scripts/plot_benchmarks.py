#!/usr/bin/env python3
"""Plot exported ChargeWise benchmark results as PNG charts.

Reads only benchmark JSON exported by the application: the "Download JSON" button on the Benchmarks page, or the
body of ``GET /api/v1/benchmarks/{id}`` saved to a file. Nothing is computed beyond what the files contain; a case
that failed verification has no timings and is left out. Several files are merged, so sizes measured in separate runs
plot together; when the same case appears in more than one file (for example, several seeds), the chart shows the
median of their medians and says how many runs it combines.

    python scripts/plot_benchmarks.py results/benchmark-12.json results/benchmark-13.json --out docs/diagrams

Each chart plots median time (log scale) against input size, with the interquartile range as error bars: the
algorithm is always blue (series 1) and the baseline always orange (series 2), the light-surface steps of the
app's chart palette, checked for colour-vision-deficiency separation.
"""

from __future__ import annotations

import argparse
import json
import statistics
from collections import defaultdict
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402  (the backend must be chosen first)
from matplotlib.ticker import FuncFormatter, NullLocator  # noqa: E402

SURFACE = "#fcfcfb"
TEXT_PRIMARY = "#0b0b0b"
TEXT_SECONDARY = "#52514e"
GRID = "#e4e3df"
ALGORITHM = "#2a78d6"
BASELINE = "#eb6834"

SIZES = ["SMALL", "MEDIUM", "LARGE"]
FAMILIES = {
    "MATCHING": ("Bipartite matching against greedy first-available", "density", "Density {} per request"),
    "POWER": ("Edmonds-Karp against greedy priority", "stations", "{} stations"),
    "POWER_FEEDERS": ("Edmonds-Karp against greedy priority, shared feeders", "stations", "{} stations"),
    "SELECTION": ("Randomized Quickselect against full sort (median)", "distribution", "{}"),
    "RANGE_QUERY": ("Segment tree against naive scan (1,000 operations)", None, "{}"),
}
DISPLAY_NAMES = {
    "BIPARTITE_MATCHING": "Bipartite matching",
    "GREEDY_FIRST_AVAILABLE": "Greedy first-available",
    "EDMONDS_KARP": "Edmonds-Karp",
    "GREEDY_PRIORITY": "Greedy priority",
    "RANDOMIZED_QUICKSELECT": "Randomized Quickselect",
    "FULL_SORT": "Full sort",
    "SEGMENT_TREE": "Segment tree",
    "NAIVE_SCAN": "Naive scan",
}
OUTPUT_NAMES = {
    "MATCHING": "benchmark-matching.png",
    "POWER": "benchmark-power.png",
    "POWER_FEEDERS": "benchmark-power-feeders.png",
    "SELECTION": "benchmark-selection.png",
    "RANGE_QUERY": "benchmark-range-query.png",
}


def load_cases(paths: list[Path]) -> tuple[list[dict], list[dict]]:
    """Every verified case from every file, and a description of each file's run."""
    cases: list[dict] = []
    runs: list[dict] = []
    for path in paths:
        document = json.loads(path.read_text(encoding="utf-8"))
        report = document.get("report", document)
        if "cases" not in report:
            raise SystemExit(f"{path}: no benchmark report found (is the run COMPLETED?)")
        runs.append({"file": path.name, "seed": report["seed"], "trials": report["trials"],
                     "java": report["environment"]["javaVersion"]})
        for case in report["cases"]:
            if not case["correct"]:
                print(f"{path.name}: skipping {case['label']}: failed verification, not timed")
                continue
            cases.append(case)
    return cases, runs


def size_label(size: str, case: dict) -> str:
    """The x tick: the size name and the count that grows with it."""
    p = case["parameters"]
    count = p.get("requests") or p.get("sessions") or p.get("n")
    return f"{size.title()}\n{count:,}"


def variant_of(case: dict, key: str | None) -> str:
    return "all" if key is None else str(case["parameters"][key])


def format_ms(value: float, _position: object = None) -> str:
    """A time in milliseconds, written in the unit that reads best (1 µs, 10 ms, 1.5 s)."""
    for unit, scale in (("s", 1000.0), ("ms", 1.0), ("µs", 0.001)):
        if value >= scale:
            return f"{value / scale:g} {unit}"
    return f"{value * 1e6:g} ns"


def median_of(cases: list[dict], side: str, field: str) -> float:
    return statistics.median(case[side][field] for case in cases)


def plot_family(family: str, cases: list[dict], out: Path) -> Path | None:
    title, key, panel_format = FAMILIES[family]
    family_cases = [case for case in cases if case["family"] == family]
    if not family_cases:
        return None
    grouped: dict[str, dict[str, list[dict]]] = defaultdict(lambda: defaultdict(list))
    for case in family_cases:
        grouped[variant_of(case, key)][case["size"]].append(case)
    variants = sorted(grouped, key=lambda v: (not v.isdigit(), int(v) if v.isdigit() else v))
    if family == "SELECTION":
        order = ["UNIFORM", "SORTED", "REVERSE_SORTED", "DUPLICATE_HEAVY", "ALL_EQUAL"]
        variants = [v for v in order if v in grouped]

    columns = len(variants)
    fig, axes = plt.subplots(1, columns, figsize=(max(3.4 * columns + 0.6, 7.0), 3.8), sharey=True, squeeze=False)
    fig.patch.set_facecolor(SURFACE)
    combined = 1
    for ax, variant in zip(axes[0], variants):
        by_size = grouped[variant]
        sizes = [s for s in SIZES if s in by_size]
        xs = list(range(len(sizes)))
        combined = max(combined, max(len(by_size[s]) for s in sizes))
        ax.set_facecolor(SURFACE)
        ends: dict[str, float] = {}
        for side, color in (("algorithm", ALGORITHM), ("baseline", BASELINE)):
            medians = [median_of(by_size[s], side, "medianNs") / 1e6 for s in sizes]
            q1 = [median_of(by_size[s], side, "q1Ns") / 1e6 for s in sizes]
            q3 = [median_of(by_size[s], side, "q3Ns") / 1e6 for s in sizes]
            errors = [[m - lo for m, lo in zip(medians, q1)], [hi - m for m, hi in zip(medians, q3)]]
            code = by_size[sizes[0]][0][side]["name"]
            name = DISPLAY_NAMES.get(code, code.replace("_", " ").capitalize())
            ax.errorbar(xs, medians, yerr=errors, color=color, linewidth=2, marker="o", markersize=6,
                        markeredgecolor=SURFACE, markeredgewidth=1.5, capsize=3, label=name)
            ends[side] = medians[-1]
        # Direct labels at the line ends, so identity never rests on colour alone; when the lines finish close
        # together (under about a factor of 1.8 apart) the labels are pushed above and below each other.
        close = max(ends.values()) / min(ends.values()) < 1.8
        upper = max(ends, key=ends.get)
        for side, end in ends.items():
            nudge = (7 if side == upper else -7) if close else 0
            ax.annotate(side, (xs[-1], end), xytext=(6, nudge), textcoords="offset points",
                        va="center", fontsize=8, color=TEXT_SECONDARY)
        ax.set_yscale("log")
        ax.yaxis.set_major_formatter(FuncFormatter(format_ms))
        ax.yaxis.set_minor_locator(NullLocator())
        ax.set_xticks(xs, [size_label(s, by_size[s][0]) for s in sizes], fontsize=8, color=TEXT_SECONDARY)
        ax.set_xlim(-0.3, len(sizes) - 0.4)
        panel = panel_format.format(variant.replace("_", " ").lower() if family == "SELECTION" else variant)
        ax.set_title(panel if key else "", fontsize=9, color=TEXT_PRIMARY, loc="left")
        ax.grid(axis="y", color=GRID, linewidth=0.8)
        ax.set_axisbelow(True)
        for spine in ("top", "right"):
            ax.spines[spine].set_visible(False)
        for spine in ("left", "bottom"):
            ax.spines[spine].set_color(GRID)
        ax.tick_params(axis="y", labelsize=8, colors=TEXT_SECONDARY)
        ax.tick_params(axis="x", length=0)
    axes[0][0].set_ylabel("Median time (log scale)", fontsize=8, color=TEXT_SECONDARY)
    handles, labels = axes[0][0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="lower right", frameon=False, fontsize=8, ncols=2)
    note = "Error bars: interquartile range." + (f" Median of {combined} runs per point." if combined > 1 else "")
    fig.suptitle(title, x=0.01, ha="left", fontsize=11, color=TEXT_PRIMARY, fontweight="semibold")
    fig.text(0.01, 0.01, note, fontsize=7.5, color=TEXT_SECONDARY)
    fig.tight_layout(rect=(0, 0.06, 1, 0.94))
    target = out / OUTPUT_NAMES[family]
    fig.savefig(target, dpi=160, facecolor=SURFACE)
    plt.close(fig)
    return target


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("files", nargs="+", type=Path, help="exported benchmark JSON files")
    parser.add_argument("--out", type=Path, default=Path("docs/diagrams"), help="directory for the PNG charts")
    args = parser.parse_args()

    cases, runs = load_cases(args.files)
    args.out.mkdir(parents=True, exist_ok=True)
    for run in runs:
        print(f"{run['file']}: seed {run['seed']}, {run['trials']} trials, Java {run['java']}")
    for family in FAMILIES:
        target = plot_family(family, cases, args.out)
        if target:
            print(f"wrote {target}")


if __name__ == "__main__":
    main()
