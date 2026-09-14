#!/usr/bin/env python3
"""GETBULK probe library for Huawei MA5608T optical/inventory tables.

Never prints the community string. Cursor comparison uses integer tuples
(the previous shell harness compared OID strings, which produced false
`non_advancing` stops such as `...23 <= ...8`).
"""
from __future__ import annotations

import os
import re
import subprocess
import time
from dataclasses import dataclass, field

OID_RE = re.compile(r"^(\.?[0-9]+(?:\.[0-9]+)+)\s*=")
ERR_TOO_BIG = re.compile(r"tooBig|too big|Message size exceeded", re.I)
ERR_TIMEOUT = re.compile(r"Timeout|No Response", re.I)
ERR_GEN = re.compile(r"genErr|General Error|Error in packet", re.I)
ERR_NOSUCH = re.compile(r"No Such Object|No Such Instance", re.I)

HOST = os.environ.get("OLT_GATEWAY_HOST", "10.11.104.2")
COMM = os.environ.get("OLT_GATEWAY_SNMP_RO_COMMUNITY", "")

DDM_BASE = "1.3.6.1.4.1.2011.6.128.1.1.2.51.1"
OPTICAL_COLUMNS = {
    "temp": f"{DDM_BASE}.1",
    "bias": f"{DDM_BASE}.2",
    "rx": f"{DDM_BASE}.4",
    "tx": f"{DDM_BASE}.5",
    "oltRx": f"{DDM_BASE}.6",
}
ONT_BASE = "1.3.6.1.4.1.2011.6.128.1.1.2.43.1"
STATE_BASE = "1.3.6.1.4.1.2011.6.128.1.1.2.46.1"
EXTRA_COLUMNS = {
    "ranging": f"{STATE_BASE}.20",
    "matchState": f"{STATE_BASE}.18",
}
INVENTORY_COLUMNS = {
    "sn": f"{ONT_BASE}.3",
    "lineProf": f"{ONT_BASE}.7",
    "srvProf": f"{ONT_BASE}.8",
    "description": f"{ONT_BASE}.9",
    "runState": f"{STATE_BASE}.15",
    "matchState": f"{STATE_BASE}.18",
    "ranging": f"{STATE_BASE}.20",
    "lastDown": f"{STATE_BASE}.24",
}


def norm(oid: str) -> str:
    return oid[1:] if oid.startswith(".") else oid


def tup(oid: str) -> tuple[int, ...]:
    return tuple(int(p) for p in norm(oid).split("."))


def under(root: str, oid: str) -> bool:
    r, o = tup(root), tup(oid)
    return o[: len(r)] == r and len(o) > len(r)


def classify(text: str) -> str | None:
    if not text.strip():
        return None
    if ERR_TOO_BIG.search(text):
        return "tooBig"
    if ERR_TIMEOUT.search(text):
        return "timeout"
    if ERR_GEN.search(text):
        return "genErr"
    if ERR_NOSUCH.search(text):
        return "noSuchObject"
    if "rror" in text:
        return "other"
    return None


@dataclass
class Page:
    ms: int
    bindings: int
    error: str | None


@dataclass
class WalkResult:
    label: str
    n_varbinds: int
    max_rep: int
    timeout_s: float
    duration_ms: int = 0
    pages: int = 0
    bindings: int = 0
    per_col: dict[str, int] = field(default_factory=dict)
    errors: dict[str, int] = field(default_factory=dict)
    page_ms: list[int] = field(default_factory=list)
    aborted: bool = False

    def result_line(self) -> str:
        cols = ",".join(f"{k}={v}" for k, v in self.per_col.items())
        errs = ",".join(f"{k}:{v}" for k, v in sorted(self.errors.items())) or "none"
        uniq = min(self.per_col.values()) if self.per_col else 0
        rps = round(uniq / (self.duration_ms / 1000.0), 2) if self.duration_ms else 0.0
        pmed = sorted(self.page_ms)[len(self.page_ms) // 2] if self.page_ms else 0
        pmax = max(self.page_ms) if self.page_ms else 0
        return (
            f"RESULT label={self.label} nVarbinds={self.n_varbinds} maxRep={self.max_rep} "
            f"timeoutS={self.timeout_s} durationMs={self.duration_ms} pages={self.pages} "
            f"bindings={self.bindings} uniqueMin={uniq} byCol={cols} errors={errs} "
            f"rowsPerSec={rps} pageMsMedian={pmed} pageMsMax={pmax} aborted={self.aborted}"
        )


def bulkget(cursors: list[str], max_rep: int, timeout_s: float, retries: int = 0):
    cmd = [
        "snmpbulkget", "-v2c", "-c", COMM, f"-Cr{max_rep}", "-Cn0",
        "-t", str(timeout_s), "-r", str(retries), "-On", "-OQ", "-Oe", HOST, *cursors,
    ]
    t0 = time.time()
    try:
        proc = subprocess.run(
            cmd, capture_output=True, text=True, errors="replace",
            timeout=timeout_s * (retries + 1) + 20,
        )
        out, err, rc = proc.stdout or "", proc.stderr or "", proc.returncode
    except subprocess.TimeoutExpired:
        out, err, rc = "", "TimeoutExpired hard kill", 124
    ms = int((time.time() - t0) * 1000)
    kind = classify(out + "\n" + err)
    if rc != 0 and kind is None:
        kind = f"rc{rc}"
    oids = []
    for line in out.splitlines():
        m = OID_RE.match(line.strip())
        if m:
            oids.append(norm(m.group(1)))
    return oids, kind, ms


def walk(
    label: str,
    roots: dict[str, str],
    max_rep: int,
    timeout_s: float,
    retries: int = 0,
    max_pages: int = 5000,
    pace_ms: int = 0,
    stop_on_error: bool = True,
) -> WalkResult:
    names = list(roots)
    res = WalkResult(label, len(names), max_rep, timeout_s)
    cursors = {n: roots[n] for n in names}
    active = {n: True for n in names}
    seen = {n: set() for n in names}
    t0 = time.time()
    while any(active.values()) and res.pages < max_pages:
        req = [n for n in names if active[n]]
        oids, kind, ms = bulkget([cursors[n] for n in req], max_rep, timeout_s, retries)
        res.pages += 1
        res.page_ms.append(ms)
        if kind:
            res.errors[kind] = res.errors.get(kind, 0) + 1
            if stop_on_error and kind in ("tooBig", "timeout", "genErr", "rc124"):
                print(
                    f"PAGEFAIL label={label} page={res.pages} nVarbinds={len(req)} "
                    f"maxRep={max_rep} error={kind} ms={ms}",
                    flush=True,
                )
                res.aborted = True
                break
        if not oids:
            res.errors["empty"] = res.errors.get("empty", 0) + 1
            break
        res.bindings += len(oids)
        best: dict[str, str] = {}
        for oid in oids:
            for n in req:
                if under(roots[n], oid):
                    seen[n].add(oid[len(norm(roots[n])) + 1:])
                    if n not in best or tup(oid) > tup(best[n]):
                        best[n] = oid
                    break
        for n in req:
            if n not in best:
                active[n] = False
            elif tup(best[n]) <= tup(cursors[n]):
                res.errors["non_advancing"] = res.errors.get("non_advancing", 0) + 1
                active[n] = False
            else:
                cursors[n] = best[n]
        if pace_ms:
            time.sleep(pace_ms / 1000.0)
    res.duration_ms = int((time.time() - t0) * 1000)
    res.per_col = {n: len(seen[n]) for n in names}
    print(res.result_line(), flush=True)
    return res


def require_comm() -> None:
    if not COMM:
        raise SystemExit("Set OLT_GATEWAY_SNMP_RO_COMMUNITY")
