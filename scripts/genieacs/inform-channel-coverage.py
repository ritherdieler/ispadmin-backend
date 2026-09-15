#!/usr/bin/env python3
"""Inform-channel coverage: which CPEs can still produce telemetry.

A fault on a channel makes GenieACS stop executing that channel for the device
until the fault is purged. Since the Inform is the only source of truth for the
360, a device faulted on the `inform` channel produces nothing at all, which is
invisible unless it is measured.

Read-only by default. `--purge` is the only mutating mode and it is explicit.
"""
import argparse
import json
import os
import urllib.error
import urllib.parse
import urllib.request

SUPPORTED_MODELS = {"F6600R", "V2804AX15T"}


def nbi_base() -> str:
    return os.environ.get("GENIEACS_NBI_URL", "http://127.0.0.1:7557").rstrip("/")


def get(base: str, path: str, **query) -> list:
    url = base + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def product_class(device_id: str) -> str:
    """GenieACS _id is OUI-ProductClass-Serial, percent-encoded per segment."""
    parts = device_id.split("-")
    return urllib.parse.unquote(parts[1]) if len(parts) >= 3 else "?"


def collect(base: str) -> dict:
    devices = get(base, "/devices/", projection="_id,_lastInform")
    faults = get(base, "/faults/")
    by_device: dict[str, list] = {}
    for fault in faults:
        by_device.setdefault(fault.get("device", ""), []).append(fault)
    models: dict[str, dict] = {}
    for device in devices:
        model = product_class(device["_id"])
        row = models.setdefault(model, {"total": 0, "blind": 0, "faulted": 0, "devices": []})
        row["total"] += 1
        channels = {f.get("channel") for f in by_device.get(device["_id"], [])}
        if channels:
            row["faulted"] += 1
        if "inform" in channels:
            row["blind"] += 1
            row["devices"].append(device["_id"])
    return {"devices": devices, "faults": faults, "models": models}


def report(data: dict) -> None:
    models = data["models"]
    total = sum(row["total"] for row in models.values())
    blind = sum(row["blind"] for row in models.values())
    supported = sum(row["total"] for name, row in models.items() if name in SUPPORTED_MODELS)
    supported_blind = sum(row["blind"] for name, row in models.items() if name in SUPPORTED_MODELS)

    print(f"devices={total} faults={len(data['faults'])} blind_on_inform={blind}")
    print(f"360-supported={supported} ({', '.join(sorted(SUPPORTED_MODELS))}) blind={supported_blind}")
    if supported:
        print(f"360 coverage: {100.0 * (supported - supported_blind) / supported:.1f}%")
    print()
    print(f"{'productClass':<16} {'total':>6} {'faulted':>8} {'blind':>6}  coverage")
    for name in sorted(models, key=lambda n: -models[n]["total"]):
        row = models[name]
        coverage = 100.0 * (row["total"] - row["blind"]) / row["total"]
        mark = " <- 360" if name in SUPPORTED_MODELS else ""
        print(f"{name:<16} {row['total']:>6} {row['faulted']:>8} {row['blind']:>6}  {coverage:>6.1f}%{mark}")

    grouped: dict[tuple, int] = {}
    for fault in data["faults"]:
        provisions = json.loads(fault.get("provisions") or "[]")
        names = ",".join(sorted({p for group in provisions for p in group})) or "-"
        key = (fault.get("code"), names, product_class(fault.get("device", "")))
        grouped[key] = grouped.get(key, 0) + 1
    if grouped:
        print()
        print("faults por code / provisions / productClass:")
        for (code, names, model), count in sorted(grouped.items(), key=lambda kv: -kv[1]):
            print(f"  {count:>3}  {code:<24} {names:<24} {model}")


def purge(base: str, faults: list, channel: str | None, code: str | None) -> None:
    targets = [
        fault for fault in faults
        if (channel is None or fault.get("channel") == channel)
        and (code is None or fault.get("code") == code)
    ]
    if not targets:
        print("nothing to purge")
        return
    for fault in targets:
        fault_id = urllib.parse.quote(fault["_id"], safe="")
        request = urllib.request.Request(base + "/faults/" + fault_id, method="DELETE")
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                print(f"deleted {fault['_id']} ({fault.get('code')}) HTTP {response.status}")
        except urllib.error.HTTPError as error:
            print(f"FAILED {fault['_id']}: HTTP {error.code}")
    print(f"purged {len(targets)} fault(s)")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="machine-readable summary")
    parser.add_argument("--purge", action="store_true", help="delete faults (mutating)")
    parser.add_argument("--channel", help="restrict --purge to one channel, e.g. inform")
    parser.add_argument("--code", help="restrict --purge to one code, e.g. too_many_commits")
    args = parser.parse_args()

    base = nbi_base()
    data = collect(base)
    if args.purge:
        purge(base, data["faults"], args.channel, args.code)
        return
    if args.json:
        print(json.dumps({
            "devices": len(data["devices"]),
            "faults": len(data["faults"]),
            "models": {name: {k: v for k, v in row.items() if k != "devices"}
                       for name, row in data["models"].items()},
        }, indent=2))
        return
    report(data)


if __name__ == "__main__":
    main()
