#!/usr/bin/env python3
"""Exact IP match helpers for MikroTik simple-queue cleanup (no substring)."""


def queue_targets_host(target: str) -> list[str]:
    hosts = []
    for part in str(target or "").replace(" ", "").split(","):
        if not part:
            continue
        hosts.append(part.split("/", 1)[0])
    return hosts


def queue_matches_ip(queue: dict, ip: str) -> bool:
    if not ip:
        return False
    return ip in queue_targets_host(str(queue.get("target", "")))
