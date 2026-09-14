#!/usr/bin/env python3
"""Phase A1: does a GETBULK whose cursor sits on the LAST ONT of a port
(so the answer must cross the ifIndex boundary) behave differently from one
whose cursor sits mid-port?

Also builds the port map (ifIndex -> ont count) using the cheap static
runState column, scoped per port so a single bad page cannot kill the map.
"""
from __future__ import annotations

import json
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import snmp_probe_lib as L  # noqa: E402

L.require_comm()
IFBASE = 0xFA000000


def ifindex(slot: int, port: int) -> int:
    return IFBASE + (slot << 13) + (port << 8)


def fsp(idx: int) -> str:
    d = idx - IFBASE
    return f"s{(d >> 13) & 0xFF}p{(d >> 8) & 0x1F}"


def scoped_walk_onts(col: str, idx: int, max_rep: int, timeout_s: float) -> tuple[list[int], str | None, int]:
    root = f"{col}.{idx}"
    cursor, onts, err, t0 = root, [], None, time.time()
    while True:
        oids, kind, _ = L.bulkget([cursor], max_rep, timeout_s, retries=0)
        if kind:
            err = kind
            break
        got = [o for o in oids if L.under(root, o)]
        if not got:
            break
        for o in got:
            onts.append(int(o.split(".")[-1]))
        nxt = max(got, key=L.tup)
        if L.tup(nxt) <= L.tup(cursor):
            err = "non_advancing"
            break
        cursor = nxt
        if len(got) < len(oids):
            break
    return sorted(set(onts)), err, int((time.time() - t0) * 1000)


print(f"MAP_START host={L.HOST}", flush=True)
ports: list[tuple[int, list[int]]] = []
t_map = time.time()
for slot in (0, 1):
    for port in range(16):
        idx = ifindex(slot, port)
        onts, err, ms = scoped_walk_onts(L.STATE_BASE + ".15", idx, 25, 8.0)
        if onts:
            ports.append((idx, onts))
            print(
                f"PORT ifIndex={idx} {fsp(idx)} onts={len(onts)} "
                f"maxOnt={max(onts)} ms={ms} err={err or 'none'}",
                flush=True,
            )
map_ms = int((time.time() - t_map) * 1000)
total = sum(len(o) for _, o in ports)
print(
    f"RESULT label=portmap_perport_runstate durationMs={map_ms} "
    f"ports={len(ports)} onts={total}",
    flush=True,
)
Path("/tmp/probe-ports.json").write_text(
    json.dumps([{"ifIndex": i, "fsp": fsp(i), "onts": o} for i, o in ports])
)

if not ports:
    raise SystemExit("no ports found")

print("BOUNDARY_START", flush=True)
for col_name, col in (("runState", L.STATE_BASE + ".15"), ("rx", L.OPTICAL_COLUMNS["rx"])):
    for pos, (idx, onts) in enumerate(ports[:6]):
        last, mid = max(onts), onts[len(onts) // 2]
        for kind_label, ont in (("boundary_lastOnt", last), ("midport", mid)):
            samples = []
            for _ in range(3):
                _, err, ms = L.bulkget([f"{col}.{idx}.{ont}"], 25, 15.0, retries=0)
                samples.append((ms, err or "none"))
                time.sleep(0.3)
            print(
                f"BOUND col={col_name} {fsp(idx)} case={kind_label} ont={ont} "
                f"samples={samples}",
                flush=True,
            )
