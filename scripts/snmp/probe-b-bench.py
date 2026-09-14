#!/usr/bin/env python3
"""Phase B: compare optical/inventory collection strategies with equal coverage.

Arms are selected by name on the command line so a single slow arm can be run
in isolation:

  full_multi5 full_multi7 perport_multi7 perport_1vb maxrep_sweep
  timeout_sweep parallel_ports inv_full_multi inv_seq

Never prints the community string.
"""
from __future__ import annotations

import json
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import snmp_probe_lib as L  # noqa: E402

L.require_comm()

OPT5 = dict(L.OPTICAL_COLUMNS)
OPT7 = {**OPT5, **L.EXTRA_COLUMNS}
UNIFIED13 = {**OPT5, **L.INVENTORY_COLUMNS, **L.EXTRA_COLUMNS}
PORTS = json.loads(Path("/tmp/probe-ports.json").read_text())
DENSE = max(PORTS, key=lambda p: len(p["onts"]))
SUMMARY = Path("/tmp/probe-b-SUMMARY.txt")
lines: list[str] = []


def record(text: str) -> None:
    print(text, flush=True)
    lines.append(text)
    SUMMARY.write_text("\n".join(lines) + "\n")


def scoped(roots: dict[str, str], if_index: int) -> dict[str, str]:
    return {n: f"{r}.{if_index}" for n, r in roots.items()}


def per_port_arm(label: str, roots: dict[str, str], max_rep: int, timeout_s: float,
                 ports=None, parallelism: int = 1, retries: int = 0) -> None:
    ports = ports if ports is not None else PORTS
    t0 = time.time()
    per_col: dict[str, int] = {n: 0 for n in roots}
    errs: dict[str, int] = {}
    failed = 0
    page_ms: list[int] = []

    def one(p):
        return p, L.walk(
            f"{label}__{p['fsp']}", scoped(roots, p["ifIndex"]),
            max_rep, timeout_s, retries=retries,
        )

    if parallelism <= 1:
        results = [one(p) for p in ports]
    else:
        with ThreadPoolExecutor(max_workers=parallelism) as ex:
            results = list(ex.map(one, ports))
    for _p, r in results:
        for n, v in r.per_col.items():
            per_col[n] += v
        for k, v in r.errors.items():
            errs[k] = errs.get(k, 0) + v
        page_ms.extend(r.page_ms)
        if r.aborted:
            failed += 1
    ms = int((time.time() - t0) * 1000)
    uniq = min(per_col.values()) if per_col else 0
    record(
        f"RESULT label={label} kind=perport nVarbinds={len(roots)} maxRep={max_rep} "
        f"timeoutS={timeout_s} retries={retries} parallel={parallelism} ports={len(ports)} "
        f"portsFailed={failed} durationMs={ms} uniqueMin={uniq} "
        f"byCol={','.join(f'{k}={v}' for k, v in per_col.items())} "
        f"errors={','.join(f'{k}:{v}' for k, v in sorted(errs.items())) or 'none'} "
        f"rowsPerSec={round(uniq / (ms / 1000.0), 2) if ms else 0} "
        f"pageMsMax={max(page_ms) if page_ms else 0}"
    )


def full_arm(label: str, roots: dict[str, str], max_rep: int, timeout_s: float,
             retries: int = 0, stop_on_error: bool = True) -> None:
    r = L.walk(label, roots, max_rep, timeout_s, retries=retries,
               stop_on_error=stop_on_error)
    record(f"RESULT label={label} kind=fulltable retries={retries} "
           + r.result_line()[len("RESULT "):])


def loss_arm(label: str, roots: dict[str, str], max_rep: int, n: int,
             timeout_s: float = 30.0) -> None:
    """Response-loss rate at a fixed cursor, retries disabled so every drop shows."""
    cursors = [f"{r}.{PORTS[0]['ifIndex']}" for r in roots.values()]
    ms_ok, lost = [], 0
    for _ in range(n):
        _oids, kind, ms = L.bulkget(cursors, max_rep, timeout_s, retries=0)
        if kind:
            lost += 1
        else:
            ms_ok.append(ms)
        time.sleep(0.5)
    srt = sorted(ms_ok)
    record(
        f"RESULT label={label} kind=lossrate nVarbinds={len(roots)} maxRep={max_rep} "
        f"n={n} lost={lost} lossPct={round(100.0 * lost / n, 1)} "
        f"msMedian={srt[len(srt) // 2] if srt else -1} "
        f"msMin={srt[0] if srt else -1} msMax={srt[-1] if srt else -1}"
    )


ARMS = sys.argv[1:] or ["full_multi5"]
record(f"BENCH_START host={L.HOST} arms={','.join(ARMS)} ports={len(PORTS)} "
       f"onts={sum(len(p['onts']) for p in PORTS)} dense={DENSE['fsp']}/{len(DENSE['onts'])} "
       f"ts={time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}")
record("NOTE community never logged")

for arm in ARMS:
    if arm == "lossrate":
        for mr in (5, 10, 25, 50):
            loss_arm(f"B0_loss_7vb_maxRep{mr}", OPT7, mr, 12, timeout_s=20.0)
        loss_arm("B0_loss_1vb_maxRep25", {"rx": OPT5["rx"]}, 25, 12, timeout_s=20.0)
    elif arm == "full_multi7_retry":
        full_arm("B2r_full_multi7_r25_t20_retry2", OPT7, 25, 20.0, retries=2)
    elif arm == "full_multi7_r10_retry":
        full_arm("B2s_full_multi7_r10_t20_retry2", OPT7, 10, 20.0, retries=2)
    elif arm == "perport_multi7_retry":
        per_port_arm("B3r_perport_multi7_r25_t20_retry2", OPT7, 25, 20.0, retries=2)
    elif arm == "full_multi5":
        full_arm("B1_full_multi5_r25_t60", OPT5, 25, 60.0)
    elif arm == "full_multi7":
        full_arm("B2_full_multi7_r25_t60", OPT7, 25, 60.0)
    elif arm == "perport_multi7":
        per_port_arm("B3_perport_multi7_r25_t60", OPT7, 25, 60.0)
    elif arm == "perport_multi5":
        per_port_arm("B3b_perport_multi5_r25_t60", OPT5, 25, 60.0)
    elif arm == "perport_1vb":
        t0 = time.time()
        tot: dict[str, int] = {}
        for name, root in OPT5.items():
            per_port_arm(f"B4_perport_1vb_{name}", {name: root}, 25, 60.0)
        record(f"RESULT label=B4_perport_1vb_total kind=perport_seq "
               f"durationMs={int((time.time() - t0) * 1000)}")
    elif arm == "maxrep_sweep":
        sample = [DENSE] + [p for p in PORTS if p is not DENSE][:2]
        for mr in (5, 10, 15, 25, 40):
            per_port_arm(f"B5_sweep_maxRep{mr}", OPT7, mr, 60.0, ports=sample)
    elif arm == "timeout_sweep":
        for to in (15.0, 30.0, 60.0):
            full_arm(f"B6_full_multi5_t{int(to)}", OPT5, 25, to)
    elif arm == "parallel_ports":
        sample = sorted(PORTS, key=lambda p: -len(p["onts"]))[:6]
        for par in (1, 2, 3):
            per_port_arm(f"B7_parallel{par}", OPT7, 25, 20.0, ports=sample,
                         parallelism=par, retries=2)
    elif arm == "inv_full_multi":
        full_arm("B8_inv_full_multi8_r25_t20_retry2", L.INVENTORY_COLUMNS, 25, 20.0,
                 retries=2)
    elif arm == "unified_perport":
        for mr in (10, 25):
            per_port_arm(f"B10_unified13vb_perport_r{mr}_t20_retry2", UNIFIED13,
                         mr, 20.0, retries=2)
    elif arm == "unified_perport_par":
        per_port_arm("B11_unified13vb_perport_r25_par2", UNIFIED13, 25, 20.0,
                     retries=2, parallelism=2)
    elif arm == "inv_perport_multi":
        per_port_arm("B8b_inv_perport_multi8_r25_t20_retry2", L.INVENTORY_COLUMNS,
                     25, 20.0, retries=2)
    elif arm == "inv_seq":
        t0 = time.time()
        for name, root in L.INVENTORY_COLUMNS.items():
            full_arm(f"B9_inv_1vb_{name}", {name: root}, 25, 60.0)
        record(f"RESULT label=B9_inv_seq_total kind=fulltable_seq "
               f"durationMs={int((time.time() - t0) * 1000)}")
    else:
        record(f"SKIP unknown arm={arm}")

record(f"BENCH_END ts={time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}")
print(f"SUMMARY_PATH={SUMMARY}", flush=True)
