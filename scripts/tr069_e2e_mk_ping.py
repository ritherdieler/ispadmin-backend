#!/usr/bin/env python3
"""MikroTik REST ping helpers for TR-069 / FIBER e2e validation."""
from __future__ import annotations

import json
from typing import Any, Optional


def is_ping_successful(results: Any, *, min_received: int = 1) -> bool:
    """
    Return True if MikroTik /rest/ping JSON shows enough replies.

    RouterOS returns a list of per-seq objects; each includes cumulative
    `received` / `sent` / `packet-loss`. We accept success when any row
    reports received >= min_received, or an explicit status of "alive".
    """
    if not isinstance(results, list) or not results:
        return False
    for row in results:
        if not isinstance(row, dict):
            continue
        status = str(row.get("status") or "").lower()
        if status == "alive":
            return True
        try:
            received = int(row.get("received") or 0)
        except (TypeError, ValueError):
            received = 0
        if received >= min_received:
            return True
    return False


def summarize_ping(results: Any) -> str:
    if not isinstance(results, list) or not results:
        return "empty"
    last = results[-1] if isinstance(results[-1], dict) else {}
    return (
        f"sent={last.get('sent')} received={last.get('received')} "
        f"packet-loss={last.get('packet-loss')} last_status={last.get('status')}"
    )


def arp_has_address(arp_rows: Any, ip: str) -> bool:
    """True if MikroTik /rest/ip/arp lists the CPE address (L2 neighbor present)."""
    if not isinstance(arp_rows, list) or not ip:
        return False
    wanted = ip.strip()
    for row in arp_rows:
        if not isinstance(row, dict):
            continue
        if str(row.get("address") or "").strip() == wanted:
            return True
    return False


def is_wan_connection_ready(connection_status: Optional[str]) -> bool:
    """TR-069 WANIPConnection.ConnectionStatus must be Connected for L3 readiness."""
    return str(connection_status or "").strip().lower() == "connected"


def parse_ping_body(body: str) -> Any:
    return json.loads(body)
