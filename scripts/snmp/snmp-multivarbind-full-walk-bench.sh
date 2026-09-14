#!/usr/bin/env bash
# Live bench: GETBULK multi-varbind full walk of 5 DDM columns vs 1-col Rx baseline.
# Does not print community. Requires OLT_GATEWAY_SNMP_RO_COMMUNITY (or source /tmp/olt-snmp-env.sh).
set -euo pipefail

HOST="${OLT_GATEWAY_HOST:-10.11.104.2}"
COMM="${OLT_GATEWAY_SNMP_RO_COMMUNITY:-}"
SUMMARY="${SUMMARY_OUT:-/tmp/snmp-multivarbind-full-SUMMARY.txt}"
LOG="${BENCH_LOG:-/tmp/snmp-multivarbind-full-$(date +%Y%m%d-%H%M%S).log}"
TIMEOUT_S="${SNMP_TIMEOUT_S:-15}"
RETRIES="${SNMP_RETRIES:-1}"

RX="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.4"
TX="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.5"
OLTRX="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.6"
TEMP="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.1"
BIAS="1.3.6.1.4.1.2011.6.128.1.1.2.51.1.2"

if [[ -z "$COMM" ]]; then
  echo "Need OLT_GATEWAY_SNMP_RO_COMMUNITY" >&2
  exit 1
fi

: >"$LOG"
{
  echo "BENCH_START host=$HOST log=$LOG $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "NOTE community/password redacted; never logged"
} | tee -a "$LOG" >/dev/null

export HOST COMM RX TX OLTRX TEMP BIAS TIMEOUT_S RETRIES LOG SUMMARY

python3 - <<'PY' | tee -a "$LOG"
import os, re, subprocess, time, tempfile, collections
from pathlib import Path

HOST = os.environ["HOST"]
COMM = os.environ["COMM"]
TIMEOUT_S = os.environ["TIMEOUT_S"]
RETRIES = os.environ["RETRIES"]
LOG = Path(os.environ["LOG"])
SUMMARY = Path(os.environ["SUMMARY"])

ROOTS = [
    ("rx", os.environ["RX"]),
    ("tx", os.environ["TX"]),
    ("oltRx", os.environ["OLTRX"]),
    ("temp", os.environ["TEMP"]),
    ("bias", os.environ["BIAS"]),
]

OID_RE = re.compile(r"^(\.?[0-9]+(?:\.[0-9]+)+)\s*=")
ERR_TOO_BIG = re.compile(r"tooBig|too big|Message size exceeded", re.I)
ERR_TIMEOUT = re.compile(r"Timeout|No Response", re.I)
ERR_GEN = re.compile(r"genErr|General Error|Error in packet", re.I)


def normalize_oid(oid: str) -> str:
    return oid[1:] if oid.startswith(".") else oid


def oid_tuple(oid: str) -> tuple[int, ...]:
    """Numeric OID ordering. Comparing OIDs as strings makes '...23' look
    smaller than '...8' and reports a false 'non_advancing' stop."""
    return tuple(int(p) for p in normalize_oid(oid).split("."))


def under(root: str, oid: str) -> bool:
    o = normalize_oid(oid)
    r = normalize_oid(root)
    return o == r or o.startswith(r + ".")


def classify_err(text: str) -> str | None:
    if not text:
        return None
    if ERR_TOO_BIG.search(text):
        return "tooBig"
    if ERR_TIMEOUT.search(text):
        return "timeout"
    if ERR_GEN.search(text):
        return "genErr"
    if "Error" in text or "error" in text:
        return "other"
    return None


def emit(line: str) -> None:
    print(line, flush=True)


def run_bulkget(cursors: list[str], max_rep: int) -> tuple[list[str], str | None, int]:
    cmd = [
        "snmpbulkget", "-v2c", "-c", COMM, f"-Cr{max_rep}",
        "-t", TIMEOUT_S, "-r", RETRIES, "-On", "-OQ", HOST, *cursors,
    ]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=int(TIMEOUT_S) + 30)
    except subprocess.TimeoutExpired:
        return [], "timeout", 124
    out = proc.stdout or ""
    err = proc.stderr or ""
    combined = out + "\n" + err
    err_kind = classify_err(combined)
    oids = []
    for line in out.splitlines():
        m = OID_RE.match(line.strip())
        if m:
            oids.append(normalize_oid(m.group(1)))
    if proc.returncode != 0 and err_kind is None:
        err_kind = "rc%d" % proc.returncode
    return oids, err_kind, proc.returncode


def walk_single_rx(max_rep: int, label: str) -> dict:
    root = ROOTS[0][1]
    tmp = tempfile.NamedTemporaryFile(prefix="snmp-rx-", suffix=".out", delete=False)
    tmp_path = Path(tmp.name)
    tmp.close()
    err_path = Path(str(tmp_path) + ".err")
    cmd = [
        "snmpbulkwalk", "-v2c", "-c", COMM, f"-Cr{max_rep}",
        "-t", TIMEOUT_S, "-r", RETRIES, "-On", "-OQ", HOST, root,
    ]
    t0 = time.time()
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
        tmp_path.write_text(proc.stdout or "")
        err_path.write_text(proc.stderr or "")
        rc = proc.returncode
    except subprocess.TimeoutExpired:
        rc = 124
        err_path.write_text("TimeoutExpired\n")
    t1 = time.time()
    lines = [ln for ln in tmp_path.read_text().splitlines() if " = " in ln]
    rows = 0
    idxs = set()
    for ln in lines:
        m = OID_RE.match(ln.strip())
        if not m:
            continue
        oid = normalize_oid(m.group(1))
        if under(root, oid):
            rows += 1
            suffix = oid[len(normalize_oid(root)) + 1 :]
            idxs.add(suffix)
    err_text = err_path.read_text() if err_path.exists() else ""
    err_kind = classify_err(err_text) or (None if rc == 0 else f"rc{rc}")
    ms = int((t1 - t0) * 1000)
    rps = (rows / (ms / 1000.0)) if ms > 0 else 0.0
    result = {
        "label": label,
        "kind": "snmp_walk_1vb",
        "nVarbinds": 1,
        "maxRep": max_rep,
        "durationMs": ms,
        "pages": "n/a",
        "bindings": rows,
        "uniqueRows": len(idxs),
        "uniqueByCol": f"rx={len(idxs)}",
        "errors": err_kind or "none",
        "rowsPerSec": round(rps, 2),
    }
    emit(
        "RESULT label={label} kind={kind} nVarbinds={nVarbinds} maxRep={maxRep} "
        "durationMs={durationMs} pages={pages} bindings={bindings} uniqueRows={uniqueRows} "
        "uniqueByCol={uniqueByCol} errors={errors} rowsPerSec={rowsPerSec}".format(**result)
    )
    tmp_path.unlink(missing_ok=True)
    err_path.unlink(missing_ok=True)
    return result


def walk_multi(max_rep: int, label: str, max_pages: int = 2000) -> dict:
    names = [n for n, _ in ROOTS]
    roots = [r for _, r in ROOTS]
    cursors = list(roots)
    active = [True] * len(roots)
    unique = {n: set() for n in names}
    pages = 0
    bindings_total = 0
    errors = collections.Counter()
    t0 = time.time()
    while any(active) and pages < max_pages:
        pages += 1
        req_cursors = [cursors[i] for i in range(len(roots)) if active[i]]
        req_idx = [i for i in range(len(roots)) if active[i]]
        oids, err_kind, rc = run_bulkget(req_cursors, max_rep)
        if err_kind:
            errors[err_kind] += 1
            if err_kind in ("tooBig", "timeout", "genErr"):
                emit(
                    f"RESULT label={label}_page_fail kind=snmp_bulkget_page page={pages} "
                    f"maxRep={max_rep} nVarbinds={len(req_cursors)} errors={err_kind} rc={rc}"
                )
                break
        if not oids:
            errors["empty"] += 1
            break
        bindings_total += len(oids)
        advanced = {i: None for i in req_idx}
        in_counts = {i: 0 for i in req_idx}
        for oid in oids:
            matched = None
            for i in req_idx:
                if under(roots[i], oid):
                    matched = i
                    break
            if matched is None:
                continue
            in_counts[matched] += 1
            suffix = oid[len(normalize_oid(roots[matched])) + 1 :]
            if suffix:
                unique[names[matched]].add(suffix)
            if advanced[matched] is None or oid_tuple(oid) > oid_tuple(advanced[matched]):
                advanced[matched] = oid
        for i in req_idx:
            if in_counts[i] == 0:
                active[i] = False
            elif advanced[i] is not None:
                if oid_tuple(advanced[i]) <= oid_tuple(cursors[i]):
                    errors["non_advancing"] += 1
                    active[i] = False
                else:
                    cursors[i] = advanced[i]
    t1 = time.time()
    ms = int((t1 - t0) * 1000)
    uniq_counts = {n: len(unique[n]) for n in names}
    min_u = min(uniq_counts.values()) if uniq_counts else 0
    max_u = max(uniq_counts.values()) if uniq_counts else 0
    rps = (min_u / (ms / 1000.0)) if ms > 0 else 0.0
    err_s = ",".join(f"{k}:{v}" for k, v in sorted(errors.items())) if errors else "none"
    col_s = ",".join(f"{n}={uniq_counts[n]}" for n in names)
    result = {
        "label": label,
        "kind": "snmp_multivar_full",
        "nVarbinds": 5,
        "maxRep": max_rep,
        "durationMs": ms,
        "pages": pages,
        "bindings": bindings_total,
        "uniqueRows": min_u,
        "uniqueByCol": col_s,
        "uniqueMin": min_u,
        "uniqueMax": max_u,
        "errors": err_s,
        "rowsPerSec": round(rps, 2),
        "activeLeft": sum(1 for a in active if a),
    }
    emit(
        "RESULT label={label} kind={kind} nVarbinds={nVarbinds} maxRep={maxRep} "
        "durationMs={durationMs} pages={pages} bindings={bindings} uniqueRows={uniqueRows} "
        "uniqueByCol={uniqueByCol} uniqueMin={uniqueMin} uniqueMax={uniqueMax} "
        "errors={errors} rowsPerSec={rowsPerSec} activeLeft={activeLeft}".format(**result)
    )
    return result


results = []
emit("== BASELINE) 1 varbind Rx full walk maxRep=25 ==")
results.append(walk_single_rx(25, "A_rx_baseline"))

emit("== MULTI) 5 varbinds × maxRep 5 full walk ==")
results.append(walk_multi(5, "M5_maxRep5"))

emit("== MULTI) 5 varbinds × maxRep 10 full walk ==")
results.append(walk_multi(10, "M5_maxRep10"))

emit("== MULTI) 5 varbinds × maxRep 15 full walk ==")
results.append(walk_multi(15, "M5_maxRep15"))

emit("== MULTI) 5 varbinds × maxRep 25 full walk (may tooBig) ==")
results.append(walk_multi(25, "M5_maxRep25"))

emit("REF label=B_5ddm_seq_prior durationMs=774056 uniqueRows=815 source=bench-20260909-090004")
emit("REF label=C_multivar_first_page_prior durationMs=2375 bindings=50 maxRep=10 source=bench-20260909-090004")
emit("BENCH_END " + time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()))

lines = [
    f"SUMMARY_WRITTEN {time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}",
    f"host={HOST}",
    "NOTE never logs community/password",
]
for r in results:
    if r["kind"] == "snmp_walk_1vb":
        lines.append(
            "RESULT label={label} kind={kind} nVarbinds={nVarbinds} maxRep={maxRep} "
            "durationMs={durationMs} pages={pages} bindings={bindings} uniqueRows={uniqueRows} "
            "uniqueByCol={uniqueByCol} errors={errors} rowsPerSec={rowsPerSec}".format(**r)
        )
    else:
        lines.append(
            "RESULT label={label} kind={kind} nVarbinds={nVarbinds} maxRep={maxRep} "
            "durationMs={durationMs} pages={pages} bindings={bindings} uniqueRows={uniqueRows} "
            "uniqueByCol={uniqueByCol} uniqueMin={uniqueMin} uniqueMax={uniqueMax} "
            "errors={errors} rowsPerSec={rowsPerSec} activeLeft={activeLeft}".format(**r)
        )
lines.append("REF label=B_5ddm_seq_prior durationMs=774056 uniqueRows=815")
lines.append("REF label=C_multivar_first_page_prior durationMs=2375 bindings=50 maxRep=10")
SUMMARY.write_text("\n".join(lines) + "\n")
emit(f"SUMMARY_PATH={SUMMARY}")
PY
