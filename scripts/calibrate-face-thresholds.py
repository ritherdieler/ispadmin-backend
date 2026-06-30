#!/usr/bin/env python3
import argparse
import json
import math
import sys
from pathlib import Path


def load_events(log_path: Path):
    events = []
    with log_path.open("r", encoding="utf-8") as handle:
        for line in handle:
            line = line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            if payload.get("event") == "face_match":
                events.append(payload)
    return events


def estimate_rates(events, threshold: float, margin: float):
    accepted = 0
    rejected_threshold = 0
    rejected_margin = 0

    for event in events:
        best_score = event.get("bestScore")
        second_score = event.get("secondBestScore")
        decision = event.get("decision")

        if best_score is None:
            continue

        if best_score < threshold:
            rejected_threshold += 1
            continue

        if second_score is not None and (best_score - second_score) < margin:
            rejected_margin += 1
            continue

        if decision == "ACCEPTED":
            accepted += 1

    total = len(events)
    return {
        "total_events": total,
        "accepted": accepted,
        "rejected_threshold": rejected_threshold,
        "rejected_margin": rejected_margin,
        "accept_rate": accepted / total if total else 0.0,
    }


def sweep_thresholds(events, margins, thresholds):
    rows = []
    for threshold in thresholds:
        for margin in margins:
            stats = estimate_rates(events, threshold, margin)
            rows.append(
                {
                    "threshold": threshold,
                    "margin": margin,
                    **stats,
                }
            )
    return rows


def main():
    parser = argparse.ArgumentParser(description="Calibrate face recognition thresholds from FACE_RECOGNITION_METRICS logs.")
    parser.add_argument("log_file", type=Path, help="Path to wispadmin-face-metrics.log")
    parser.add_argument("--threshold-min", type=float, default=0.78)
    parser.add_argument("--threshold-max", type=float, default=0.90)
    parser.add_argument("--threshold-step", type=float, default=0.01)
    parser.add_argument("--margin-min", type=float, default=0.03)
    parser.add_argument("--margin-max", type=float, default=0.10)
    parser.add_argument("--margin-step", type=float, default=0.01)
    args = parser.parse_args()

    if not args.log_file.exists():
        print(f"Log file not found: {args.log_file}", file=sys.stderr)
        sys.exit(1)

    events = load_events(args.log_file)
    if not events:
        print("No face_match events found in log.", file=sys.stderr)
        sys.exit(1)

    thresholds = []
    value = args.threshold_min
    while value <= args.threshold_max + 1e-9:
        thresholds.append(round(value, 4))
        value += args.threshold_step

    margins = []
    value = args.margin_min
    while value <= args.margin_max + 1e-9:
        margins.append(round(value, 4))
        value += args.margin_step

    rows = sweep_thresholds(events, margins, thresholds)
    best = max(rows, key=lambda row: row["accept_rate"])

    print(f"Loaded {len(events)} face_match events from {args.log_file}")
    print("")
    print("Suggested operating point (max accept_rate in sweep):")
    print(
        f"  threshold={best['threshold']:.2f} margin={best['margin']:.2f} "
        f"accept_rate={best['accept_rate']:.3f} accepted={best['accepted']} total={best['total_events']}"
    )
    print("")
    print("Top 10 combinations:")
    for row in sorted(rows, key=lambda item: item["accept_rate"], reverse=True)[:10]:
        print(
            f"  threshold={row['threshold']:.2f} margin={row['margin']:.2f} "
            f"accept_rate={row['accept_rate']:.3f} rejected_threshold={row['rejected_threshold']} "
            f"rejected_margin={row['rejected_margin']}"
        )


if __name__ == "__main__":
    main()
