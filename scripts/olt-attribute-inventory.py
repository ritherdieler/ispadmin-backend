#!/usr/bin/env python3
"""Read-only OLT attribute inventory over a single SSH session."""
from __future__ import annotations

import re
import sys
import time
from pathlib import Path

import pexpect

HOST = sys.argv[1] if len(sys.argv) > 1 else "10.11.104.2"
USER = sys.argv[2] if len(sys.argv) > 2 else "root"
PASS = sys.argv[3] if len(sys.argv) > 3 else "admin"
ENABLE_PASS = sys.argv[4] if len(sys.argv) > 4 else PASS
OUT = Path("/tmp/olt-attribute-inventory-raw.txt")

SSH_OPTS = (
    "-o StrictHostKeyChecking=no "
    "-o PreferredAuthentications=password "
    "-o PubkeyAuthentication=no "
    "-o KexAlgorithms=diffie-hellman-group-exchange-sha1 "
    "-o HostKeyAlgorithms=ssh-rsa "
    "-o PubkeyAcceptedKeyTypes=ssh-rsa "
    "-o Ciphers=aes128-cbc "
    "-o ConnectionAttempts=1"
)

PROMPT = re.compile(r"MA5608T(?P<mode>(?:\(config(?:-[\w./-]+)?\))?)[>#]\s*$", re.M)
MORE = re.compile(r"---- More \( Press 'Q' to break \) ----|More \( Press 'Q' to break \)")


def append(title: str | None, text: str) -> None:
    with OUT.open("a", encoding="utf-8", errors="replace") as f:
        if title:
            f.write(f"\n\n========== {title} ==========\n")
        f.write(text)


def drain_more(child: pexpect.spawn, buf: str, timeout: float) -> str:
    deadline = time.time() + timeout
    while time.time() < deadline:
        if MORE.search(buf[-400:]):
            child.send(" ")
            try:
                chunk = child.read_nonblocking(size=65536, timeout=2)
                buf += chunk.decode("utf-8", "replace") if isinstance(chunk, bytes) else chunk
            except pexpect.TIMEOUT:
                continue
            except pexpect.EOF:
                break
            continue
        if PROMPT.search(buf[-300:]):
            break
        try:
            chunk = child.read_nonblocking(size=65536, timeout=1)
            buf += chunk.decode("utf-8", "replace") if isinstance(chunk, bytes) else chunk
        except pexpect.TIMEOUT:
            if PROMPT.search(buf[-300:]):
                break
        except pexpect.EOF:
            break
    return buf


def wait_prompt(child: pexpect.spawn, timeout: float = 60) -> str:
    buf = ""
    deadline = time.time() + timeout
    while time.time() < deadline:
        try:
            chunk = child.read_nonblocking(size=65536, timeout=1)
            buf += chunk.decode("utf-8", "replace") if isinstance(chunk, bytes) else chunk
        except pexpect.TIMEOUT:
            pass
        except pexpect.EOF:
            break
        buf = drain_more(child, buf, max(1.0, deadline - time.time()))
        if PROMPT.search(buf[-300:]) and not MORE.search(buf[-400:]):
            return buf
    return buf


def run(child: pexpect.spawn, cmd: str, title: str, timeout: float = 90) -> str:
    child.send(cmd + "\r")
    time.sleep(0.2)
    out = wait_prompt(child, timeout=timeout)
    append(title, out)
    return out


def main() -> int:
    OUT.write_text("", encoding="utf-8")
    cmd = f"ssh {SSH_OPTS} {USER}@{HOST}"
    child = pexpect.spawn(cmd, encoding=None, timeout=30)
    child.delaybeforesend = 0.05

    boot = ""
    while True:
        try:
            chunk = child.read_nonblocking(size=4096, timeout=10)
            boot += chunk.decode("utf-8", "replace") if isinstance(chunk, bytes) else chunk
        except pexpect.TIMEOUT:
            if re.search(r"(?i)password:", boot[-200:]):
                break
            append("CONNECT TIMEOUT", boot)
            return 1
        except pexpect.EOF:
            append("CONNECT EOF", boot)
            return 1
        if re.search(r"(?i)password:", boot[-200:]):
            break
        if "Reenter times" in boot or "upper limit" in boot:
            append("SSH LOCKED", boot)
            return 2

    child.send(PASS + "\r")
    user_prompt = wait_prompt(child, 45)
    append("LOGIN", user_prompt)
    if not PROMPT.search(user_prompt[-300:]):
        return 1

    child.send("enable\r")
    en = ""
    deadline = time.time() + 30
    while time.time() < deadline:
        try:
            chunk = child.read_nonblocking(size=4096, timeout=1)
            en += chunk.decode("utf-8", "replace") if isinstance(chunk, bytes) else chunk
        except pexpect.TIMEOUT:
            pass
        except pexpect.EOF:
            break
        if re.search(r"(?i)password:", en[-200:]):
            child.send(ENABLE_PASS + "\r")
            en += wait_prompt(child, 30)
            break
        if PROMPT.search(en[-300:]):
            break
    append("ENABLE", en)

    for cmd, title, to in [
        ("scroll 512", "INIT scroll", 20),
        ("mmi-mode enable", "INIT mmi-mode", 20),
        ("display version", "OLT display version", 60),
        ("display patch-information", "OLT display patch-information", 40),
        ("display time", "OLT display time", 20),
        ("display board 0", "OLT display board 0", 60),
        ("display board 1", "OLT display board 1", 30),
        ("display user-interface maximum-vty", "OLT maximum-vty", 20),
        ("display users", "OLT display users", 30),
        ("display snmp-agent sys-info", "OLT snmp-agent sys-info", 30),
        ("display device", "OLT display device", 40),
        ("display temperature", "OLT display temperature", 30),
        ("display power", "OLT display power", 30),
        ("display vlan summary", "OLT display vlan summary", 40),
        ("display ont-lineprofile gpon all", "OLT ont-lineprofile all", 60),
        ("display ont-srvprofile gpon all", "OLT ont-srvprofile all", 60),
        ("display dba-profile all", "OLT dba-profile all", 60),
        ("display ont autofind all", "OLT ont autofind all", 60),
        ("display ont info 0 0 0", "ONU display ont info 0/0/0", 90),
        ("display ont info by-sn ZTEGDC47DAD1", "ONU by-sn ZTEGDC47DAD1", 60),
        ("config", "ENTER config", 20),
        ("interface gpon 0/0", "ENTER gpon 0/0", 20),
        ("display port state all", "GPON port state all", 60),
        ("display ont info 0 0", "GPON ont info 0 0", 90),
        ("display ont optical-info 0 0", "GPON optical-info 0 0", 60),
        ("display ont version 0 0", "GPON ont version 0 0", 40),
        ("display ont capability 0 0", "GPON ont capability 0 0", 40),
        ("display ont wan-info 0 0", "GPON ont wan-info 0 0", 40),
        ("display ont port attribute 0 0 eth 1", "GPON ont eth1 attribute", 40),
        ("quit", "EXIT gpon", 20),
        ("quit", "EXIT config", 20),
        ("quit", "LOGOUT enable", 20),
    ]:
        try:
            run(child, cmd, title, timeout=to)
        except Exception as ex:  # noqa: BLE001
            append(f"ERROR {title}", str(ex))
            break

    try:
        if child.isalive():
            child.send("y\r")
            time.sleep(0.5)
            child.close(force=True)
    except Exception:
        pass

    print(f"DONE -> {OUT} ({OUT.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
