#!/usr/bin/env python3
"""Explicit, reversible pilot deployment. Default is a local dry run; no CPE tasks."""
import argparse
import json
import os
from pathlib import Path
import re
import urllib.request

NAME = "gigafiber-wifi-telemetry"
SUPPORTED_MODELS = {"F6600R", "V2804AX15T"}
DEVICE_ID_PATTERN = re.compile(r"^[A-Za-z0-9]+-([A-Za-z0-9]+)-([A-Za-z0-9._-]+)$")


def precondition(device_ids):
    """Build a GenieACS preset expression, never a MongoDB query.

    GenieACS evaluates preset preconditions for every CWMP Inform. The
    expression language supports DeviceID comparisons, not Mongo operators
    such as $in. Keep the allowlist exact by pairing serial and product class.
    """
    clauses = []
    for device_id in dict.fromkeys(device_ids):
        match = DEVICE_ID_PATTERN.fullmatch(device_id)
        if match is None:
            raise ValueError(f"Invalid GenieACS device ID: {device_id!r}")
        model, serial = match.groups()
        if model.upper() not in SUPPORTED_MODELS:
            raise ValueError(f"Unsupported Wi-Fi telemetry model: {model}")
        clauses.append(
            f'(DeviceID.SerialNumber = "{serial}" AND DeviceID.ProductClass = "{model}")'
        )
    return " OR ".join(clauses)


def preset(device_ids):
    if not device_ids:
        raise ValueError("At least one explicit pilot device ID is required")
    return {"weight": 20, "channel": NAME, "events": {"2 PERIODIC": True},
            "precondition": precondition(device_ids),
            "configurations": [{"type": "provision", "name": NAME, "args": []}]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device-id", action="append", default=[])
    parser.add_argument("--apply", action="store_true")
    parser.add_argument("--disable", action="store_true")
    args = parser.parse_args()
    config = None if args.disable else preset(args.device_id)
    if not args.apply:
        print(json.dumps({"dry_run": True, "disable": args.disable, "preset": config}, indent=2))
        return
    base = os.environ.get("GENIEACS_NBI_URL", "http://127.0.0.1:7557").rstrip("/")
    if args.disable:
        operations = [("presets", None, "DELETE")]
    else:
        source = Path(__file__).with_name("provisions").joinpath(NAME + ".js").read_bytes()
        operations = [("provisions", source, "PUT"), ("presets", json.dumps(config).encode(), "PUT")]
    for collection, data, method in operations:
        req = urllib.request.Request(base + "/" + collection + "/" + NAME, data=data, method=method)
        req.add_header("Content-Type", "application/javascript" if collection == "provisions" else "application/json")
        with urllib.request.urlopen(req, timeout=10) as response:
            print(collection, response.status)


if __name__ == "__main__":
    main()
