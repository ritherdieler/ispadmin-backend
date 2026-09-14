#!/usr/bin/env python3
"""Phase C: can inventory + optical share ONE per-port multi-varbind pass?

Arms:
  dedup          column set arithmetic, no network
  pdu_limit      nVarbinds x maxRep sweep looking for tooBig / degradation
  page_latency   fixed-cursor page latency 7 vs 8 vs 13 vs 15 varbinds
  unified32      per-port 13 vb over all 32 GPON ports (merged arm)
  unified22      per-port 13 vb over the 22 ports that hold ONTs
  empty10        per-port 13 vb over the 10 ports with no ONTs
  unified32_t6   merged arm with a short timeout (Part 4)
  optical22_ref  reference: per-port 7 vb over 22 ports (today's optical pass)

Never prints the community string.
"""
from __future__ import annotations

import json
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import snmp_probe_lib as L  # noqa: E402

L.require_comm()

IFBASE = 0xFA000000
SUMMARY = Path("/tmp/probe-c-SUMMARY.txt")
lines: list[str] = []


def record(text: str) -> None:
    print(text, flush=True)
    lines.append(text)
    SUMMARY.write_text("\n".join(lines) + "\n")


def fsp(idx: int) -> str:
    d = idx - IFBASE
    return f"s{(d >> 13) & 0xFF}p{(d >> 8) & 0x1F}"


def ifindex(slot: int, port: int) -> int:
    return IFBASE + (slot << 13) + (port << 8)


KNOWN = {p["ifIndex"]: p for p in json.loads(Path("/tmp/probe-ports.json").read_text())}
ALL32 = [
    KNOWN.get(ifindex(s, p), {"ifIndex": ifindex(s, p), "fsp": fsp(ifindex(s, p)), "onts": []})
    for s in (0, 1)
    for p in range(16)
]
POPULATED = [p for p in ALL32 if p["onts"]]
EMPTY = [p for p in ALL32 if not p["onts"]]
DENSE = max(POPULATED, key=lambda p: len(p["onts"]))

OPT5 = dict(L.OPTICAL_COLUMNS)
OPT7 = {**OPT5, **L.EXTRA_COLUMNS}
INV8 = dict(L.INVENTORY_COLUMNS)
UNIFIED13 = {**OPT5, **INV8, **L.EXTRA_COLUMNS}
NAIVE15 = list(OPT7.items()) + list(INV8.items())


def cursors_at(pairs, port, ont) -> list[str]:
    return [f"{root}.{port['ifIndex']}.{ont}" for _n, root in pairs]


def sample_page(label: str, pairs, max_rep: int, n: int, timeout_s: float,
                port=None, ont=None) -> None:
    port = port or DENSE
    ont = ont if ont is not None else port["onts"][len(port["onts"]) // 2]
    cur = cursors_at(pairs, port, ont)
    ms_ok: list[int] = []
    binds: list[int] = []
    errs: dict[str, int] = {}
    for _ in range(n):
        oids, kind, ms = L.bulkget(cur, max_rep, timeout_s, retries=0)
        if kind:
            errs[kind] = errs.get(kind, 0) + 1
        else:
            ms_ok.append(ms)
            binds.append(len(oids))
        time.sleep(0.3)
    srt = sorted(ms_ok)
    med = srt[len(srt) // 2] if srt else -1
    rows = round(max_rep, 2)
    per_row = round(med / rows, 1) if med > 0 else -1
    record(
        f"RESULT label={label} kind=page nVarbinds={len(pairs)} maxRep={max_rep} "
        f"askedBindings={len(pairs) * max_rep} n={n} ok={len(ms_ok)} "
        f"lost={sum(errs.values())} errors={','.join(f'{k}:{v}' for k, v in sorted(errs.items())) or 'none'} "
        f"msMedian={med} msMin={srt[0] if srt else -1} msMax={srt[-1] if srt else -1} "
        f"msStdev={round(statistics.pstdev(ms_ok), 1) if len(ms_ok) > 1 else 0} "
        f"gotBindingsMedian={sorted(binds)[len(binds) // 2] if binds else -1} "
        f"msPerOntRow={per_row}"
    )


def per_port_arm(label: str, roots: dict[str, str], max_rep: int, timeout_s: float,
                 ports, retries: int = 2) -> None:
    t0 = time.time()
    per_col = {n: 0 for n in roots}
    errs: dict[str, int] = {}
    page_ms: list[int] = []
    failed = 0
    for p in ports:
        scoped = {n: f"{r}.{p['ifIndex']}" for n, r in roots.items()}
        pt = time.time()
        r = L.walk(f"{label}__{p['fsp']}", scoped, max_rep, timeout_s, retries=retries)
        pms = int((time.time() - pt) * 1000)
        for n, v in r.per_col.items():
            per_col[n] += v
        for k, v in r.errors.items():
            errs[k] = errs.get(k, 0) + v
        page_ms.extend(r.page_ms)
        if r.aborted:
            failed += 1
        record(
            f"PORT label={label} fsp={p['fsp']} expectedOnts={len(p['onts'])} "
            f"ms={pms} pages={r.pages} rows={min(r.per_col.values()) if r.per_col else 0} "
            f"maxCol={max(r.per_col.values()) if r.per_col else 0} "
            f"errors={','.join(f'{k}:{v}' for k, v in sorted(r.errors.items())) or 'none'}"
        )
    ms = int((time.time() - t0) * 1000)
    uniq = min(per_col.values()) if per_col else 0
    record(
        f"RESULT label={label} kind=perport nVarbinds={len(roots)} maxRep={max_rep} "
        f"timeoutS={timeout_s} retries={retries} ports={len(ports)} portsFailed={failed} "
        f"durationMs={ms} uniqueMin={uniq} maxCol={max(per_col.values()) if per_col else 0} "
        f"byCol={','.join(f'{k}={v}' for k, v in per_col.items())} "
        f"errors={','.join(f'{k}:{v}' for k, v in sorted(errs.items())) or 'none'} "
        f"pageMsMedian={sorted(page_ms)[len(page_ms) // 2] if page_ms else 0} "
        f"pageMsMax={max(page_ms) if page_ms else 0}"
    )


ARMS = sys.argv[1:] or ["dedup"]
record(f"PROBE_C_START host={L.HOST} arms={','.join(ARMS)} ports32={len(ALL32)} "
       f"populated={len(POPULATED)} empty={len(EMPTY)} "
       f"onts={sum(len(p['onts']) for p in ALL32)} dense={DENSE['fsp']}/{len(DENSE['onts'])} "
       f"ts={time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}")
record("NOTE community never logged")

for arm in ARMS:
    if arm == "dedup":
        inter = sorted(set(OPT7.values()) & set(INV8.values()))
        record(f"RESULT label=C0_dedup kind=static inv={len(INV8)} opt={len(OPT7)} "
               f"naiveSum={len(NAIVE15)} unique={len(UNIFIED13)} "
               f"duplicated={len(inter)} duplicatedOids={';'.join(inter)}")
        record(f"NOTE unifiedColumns={','.join(sorted(UNIFIED13))}")
        record(f"NOTE emptyPorts={','.join(p['fsp'] for p in EMPTY)}")
    elif arm == "pdu_limit":
        sets = [("7vb", list(OPT7.items())), ("8vb", list(INV8.items())),
                ("13vb", list(UNIFIED13.items())), ("15vb", NAIVE15)]
        for name, pairs in sets:
            for mr in (10, 15, 20, 25):
                sample_page(f"C1_pdu_{name}_r{mr}", pairs, mr, 4, 20.0)
    elif arm == "page_latency":
        for name, pairs in (("7vb", list(OPT7.items())), ("8vb", list(INV8.items())),
                            ("13vb", list(UNIFIED13.items())), ("15vb", NAIVE15)):
            sample_page(f"C2_lat_{name}_r25", pairs, 25, 12, 20.0)
    elif arm == "unified32":
        per_port_arm("C3_unified13vb_32ports_r25_t20_retry2", UNIFIED13, 25, 20.0, ALL32)
    elif arm == "unified22":
        per_port_arm("C3b_unified13vb_22ports_r25_t20_retry2", UNIFIED13, 25, 20.0, POPULATED)
    elif arm == "empty10":
        per_port_arm("C4_unified13vb_empty10_r25_t20_retry2", UNIFIED13, 25, 20.0, EMPTY)
    elif arm == "unified32_t6":
        per_port_arm("C5_unified13vb_32ports_r25_t6_retry3", UNIFIED13, 25, 6.0, ALL32,
                     retries=3)
    elif arm == "optical22_ref":
        per_port_arm("C6_optical7vb_22ports_r25_t20_retry2", OPT7, 25, 20.0, POPULATED)
    elif arm == "ab_interleaved":
        sample = [p for p in POPULATED if p["fsp"] in ("s0p0", "s0p13", "s0p14")]
        onts = sum(len(p["onts"]) for p in sample)
        totals = {"7vb": [], "13vb": []}
        for rnd in range(3):
            for name, roots in (("13vb", UNIFIED13), ("7vb", OPT7)):
                t0 = time.time()
                for p in sample:
                    L.walk(f"C9_ab_{name}_r{rnd}__{p['fsp']}",
                           {n: f"{r}.{p['ifIndex']}" for n, r in roots.items()}, 25, 20.0, retries=2)
                ms = int((time.time() - t0) * 1000)
                totals[name].append(ms)
                record(f"AB round={rnd} arm={name} ports={len(sample)} onts={onts} ms={ms} "
                       f"msPerOnt={round(ms / onts, 1)}")
        for name, vals in totals.items():
            record(f"RESULT label=C9_ab_{name} kind=ab rounds={len(vals)} onts={onts} "
                   f"msMedian={sorted(vals)[len(vals) // 2]} msValues={vals} "
                   f"msPerOntMedian={round(sorted(vals)[len(vals) // 2] / onts, 1)}")
    elif arm == "empty_probe":
        for name, roots in (("13vb", UNIFIED13), ("8vb", INV8), ("1vb", {"runState": INV8["runState"]})):
            t0 = time.time()
            per_port = []
            for p in EMPTY:
                pt = time.time()
                L.walk(f"C8_empty_{name}__{p['fsp']}", {n: f"{r}.{p['ifIndex']}" for n, r in roots.items()},
                       25, 20.0, retries=2)
                per_port.append(int((time.time() - pt) * 1000))
            tot = int((time.time() - t0) * 1000)
            record(f"RESULT label=C8_emptyprobe_{name} kind=emptyports nVarbinds={len(roots)} "
                   f"ports={len(EMPTY)} durationMs={tot} msPerPort={tot // max(len(EMPTY), 1)} "
                   f"msMax={max(per_port) if per_port else 0}")
    elif arm == "inv_timeout_sweep":
        for to, rt in ((20.0, 2), (5.0, 4), (3.0, 6), (2.0, 8)):
            r = L.walk(f"C7_inv8vb_r25_t{to:g}_retry{rt}", INV8, 25, to, retries=rt)
            record(f"RESULT label=C7_inv8vb_r25_t{to:g}_retry{rt} kind=fulltable "
                   f"timeoutS={to} retries={rt} " + r.result_line()[len("RESULT "):])
            time.sleep(2)
    else:
        record(f"SKIP unknown arm={arm}")

record(f"PROBE_C_END ts={time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}")
print(f"SUMMARY_PATH={SUMMARY}", flush=True)
