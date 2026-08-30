#!/usr/bin/env python3
"""Explicit, reversible pilot deployment. Default is a local dry run; no CPE tasks."""
import argparse
import json
import os
from pathlib import Path
import urllib.request

NAME = "gigafiber-wifi-telemetry"


def preset(device_ids):
    if not device_ids:
        raise ValueError("At least one explicit pilot device ID is required")
    return {"weight": 20, "channel": NAME, "events": {"2 PERIODIC": True},
            "precondition": json.dumps({"$and": [{"_id": {"$in": device_ids}},
                {"_deviceId._ProductClass": {"$in": ["F6600R", "V2804AX15T"]}}]}),
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
