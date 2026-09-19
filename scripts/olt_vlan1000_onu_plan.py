#!/usr/bin/env python3
from collections import Counter

PROFILES = {
    "Generic_1_V100M1000MGM": {
        "id": 12,
        "gemport": 2,
        "maps_1000": True,
        "mapping_cmd": None,
    },
    "Generic_1_HD446E747": {
        "id": 14,
        "gemport": 1,
        "maps_1000": True,
        "mapping_cmd": None,
    },
    "Generic_1_HFD44D20E": {
        "id": 13,
        "gemport": 1,
        "maps_1000": True,
        "mapping_cmd": None,
    },
    "Generic_1_HD6BD1AC0": {
        "id": 18,
        "gemport": 1,
        "maps_1000": True,
        "mapping_cmd": None,
    },
    "Generic_1_HF291F96D": {
        "id": 5,
        "gemport": 1,
        "maps_1000": False,
        "mapping_cmd": "gem mapping 1 3 vlan 1000",
    },
    "Generic_1_V100": {
        "id": 6,
        "gemport": 2,
        "maps_1000": False,
        "mapping_cmd": "gem mapping 2 1 vlan 1000",
    },
    "Generic_1_V1": {
        "id": 3,
        "gemport": 1,
        "maps_1000": False,
        "mapping_cmd": "gem mapping 1 2 vlan 1000",
    },
    "line-profile_10": {
        "id": 10,
        "gemport": 1,
        "maps_1000": False,
        "mapping_cmd": "gem mapping 1 1 vlan 1000",
    },
    "SmartOLT_G_V100": {
        "id": 7,
        "gemport": 1,
        "maps_1000": False,
        "mapping_cmd": "gem mapping 1 2 vlan 1000",
    },
    "SmartOLT_G": {
        "id": 4,
        "gemport": 1,
        "maps_1000": True,
        "mapping_cmd": None,
    },
    "SMARTOLT_FLEXIBLE_GPON": {
        "id": 1,
        "gemport": 1,
        "maps_1000": True,
        "mapping_cmd": None,
    },
}


def classify(onu):
    if onu.get("has_sp_1000"):
        return {
            "kind": "skip_already",
            "sn": onu.get("sn"),
            "line_profile": onu.get("line_profile"),
            "gemport": None,
            "needs_mapping": False,
            "mapping_cmd": None,
            "profile_id": None,
        }
    spec = PROFILES.get(onu.get("line_profile") or "")
    if spec is None:
        return {
            "kind": "manual",
            "sn": onu.get("sn"),
            "line_profile": onu.get("line_profile"),
            "gemport": None,
            "needs_mapping": False,
            "mapping_cmd": None,
            "profile_id": None,
        }
    if spec["maps_1000"]:
        return {
            "kind": "add_sp",
            "sn": onu.get("sn"),
            "line_profile": onu.get("line_profile"),
            "gemport": spec["gemport"],
            "needs_mapping": False,
            "mapping_cmd": None,
            "profile_id": spec["id"],
        }
    return {
        "kind": "add_mapping_then_sp",
        "sn": onu.get("sn"),
        "line_profile": onu.get("line_profile"),
        "gemport": spec["gemport"],
        "needs_mapping": True,
        "mapping_cmd": spec["mapping_cmd"],
        "profile_id": spec["id"],
    }


def summarize(plan):
    counts = Counter(item["kind"] for item in plan)
    return {
        "skip_already": counts.get("skip_already", 0),
        "add_sp": counts.get("add_sp", 0),
        "add_mapping_then_sp": counts.get("add_mapping_then_sp", 0),
        "manual": counts.get("manual", 0),
        "total": len(plan),
    }


def plan_onus(onus):
    return [classify(onu) for onu in onus]


def mapping_cmds_needed(plan):
    seen = {}
    for item in plan:
        if item["kind"] != "add_mapping_then_sp":
            continue
        seen[item["profile_id"]] = item["mapping_cmd"]
    return seen
