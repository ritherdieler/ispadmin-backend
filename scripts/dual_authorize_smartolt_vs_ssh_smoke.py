#!/usr/bin/env python3
"""Dual authorize smoke: SmartOLT cloud vs SSH Gateway CLI; GenieACS inform check."""
from __future__ import annotations

import json
import ssl
import subprocess
import time
import urllib.parse
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

SN = "ZTEGDC47BFFD"
BOARD = "1"
PORT = "6"
ONT_ID = "16"
VLAN = "100"
OLT_SMARTOLT_ID = "2"
ACS_QUERY = urllib.parse.quote(json.dumps({"_id": {"$regex": SN}}))
CTX = ssl._create_unverified_context()


def load_secrets() -> dict[str, str]:
    secrets: dict[str, str] = {}
    for line in Path("/tmp/gigafiber-prod-secrets.env").read_text().splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            secrets[k] = v
    return secrets


def smartolt_post(secrets: dict[str, str], path: str, form: dict[str, str] | None = None) -> dict:
    base = secrets["OLT_SERVICE_BASE_URL"].rstrip("/") + "/"
    url = base + path.lstrip("/")
    headers = {"X-Token": secrets["OLT_SERVICE_API_KEY"], "Accept": "application/json"}
    data = None
    if form is not None:
        data = urllib.parse.urlencode(form).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded"
    req = urllib.request.Request(url, data=data, headers=headers, method="POST")
    with urllib.request.urlopen(req, timeout=30, context=CTX) as r:
        body = r.read().decode()
        return json.loads(body) if body else {}


def genieacs_last_inform() -> str | None:
    url = f"http://127.0.0.1:7557/devices/?query={ACS_QUERY}"
    with urllib.request.urlopen(url, timeout=10) as r:
        data = json.loads(r.read().decode())
    if not data:
        return None
    return data[0].get("_lastInform")


def olt_expect(commands: list[str], grep: str | None = None) -> str:
    script = r"""
set timeout 45
log_user 0
spawn ssh -o StrictHostKeyChecking=no -o PreferredAuthentications=password -o PubkeyAuthentication=no \
  -o KexAlgorithms=diffie-hellman-group-exchange-sha1 -o HostKeyAlgorithms=ssh-rsa \
  -o PubkeyAcceptedKeyTypes=ssh-rsa -o Ciphers=aes128-cbc oltadmin@10.11.104.2
expect { -re "(?i)password:" { send -- "GigaOlt2026\r" } timeout { puts "LOGIN_FAIL"; exit 1 } }
expect -re {MA5608T[>#]}
send -- "enable\r"; expect -re {MA5608T#}
send -- "config\r"; expect -re {MA5608T\(config\)#}
send -- "mmi-mode enable\r"; expect -re {MA5608T\(config\)#}
send -- "scroll 512\r"; expect -re {MA5608T\(config\)#}
log_user 1
"""
    for cmd in commands:
        # Escape for Tcl double-quoted send
        escaped = cmd.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$").replace("[", "\\[")
        script += f'send -- "{escaped}\\r"\n'
        script += (
            "expect {\n"
            "  -re {---- More} { send -- \" \"; exp_continue }\n"
            "  -re {More \\( Press} { send -- \" \"; exp_continue }\n"
            "  -re {MA5608T\\(config\\)#} {}\n"
            "  -re {MA5608T\\(config-if-gpon-} { }\n"
            "  timeout { puts \"CMD_TIMEOUT\"; }\n"
            "}\n"
        )
    script += 'send -- "quit\\r"; expect { -re {MA5608T#} {} -re {MA5608T\\(config\\)#} { send -- "quit\\r"; exp_continue } timeout {} }\n'
    script += 'send -- "quit\\r"; expect eof\n'
    proc = subprocess.run(
        ["expect", "-c", script],
        capture_output=True,
        text=True,
        timeout=180,
    )
    out = proc.stdout + proc.stderr
    if grep:
        needles = grep.split("|")
        lines = [ln for ln in out.splitlines() if any(g in ln for g in needles)]
        return "\n".join(lines)
    return out


def olt_summary() -> str:
    # Avoid Huawei "| include a|b|c" (long filters hang the CLI).
    return olt_expect(
        [f"display ont info by-sn {SN}"],
        grep="ONT-ID|Run state|Line profile|Service profile|Description|Match state|does not exist",
    )


def olt_delete() -> None:
    olt_expect(
        [
            f"undo service-port port 0/{BOARD}/{PORT} ont {ONT_ID}",
            f"interface gpon 0/{BOARD}",
            f"ont delete {PORT} {ONT_ID}",
            "quit",
        ]
    )


def olt_authorize_ssh() -> None:
    day = datetime.now(timezone.utc).strftime("%Y%m%d")
    desc = f"SSH-DUAL_zone_Zone 1_authd_{day}"
    olt_expect(
        [
            f"interface gpon 0/{BOARD}",
            f'ont add {PORT} {ONT_ID} sn-auth {SN} omci ont-lineprofile-id 6 ont-srvprofile-id 13 desc "{desc}"',
            "quit",
            f"service-port vlan {VLAN} gpon 0/{BOARD}/{PORT} ont {ONT_ID} gemport 1 multi-service user-vlan {VLAN} "
            "tag-transform translate inbound traffic-table index 8 outbound traffic-table index 9",
        ]
    )


def wait_online(label: str, timeout_s: int = 90) -> str:
    deadline = time.time() + timeout_s
    last = ""
    while time.time() < deadline:
        last = olt_summary()
        if "Run state               : online" in last or "Run state" in last and "online" in last:
            print(f"[{label}] OLT online")
            return last
        time.sleep(5)
    print(f"[{label}] OLT not online within {timeout_s}s")
    return last


def wait_inform_after(label: str, before: str | None, timeout_s: int = 180) -> str | None:
    deadline = time.time() + timeout_s
    while time.time() < deadline:
        now = genieacs_last_inform()
        if now and now != before:
            print(f"[{label}] GenieACS new lastInform={now} (was {before})")
            return now
        time.sleep(10)
        print(f"[{label}] waiting GenieACS inform... current={now}")
    return genieacs_last_inform()


def main() -> None:
    secrets = load_secrets()
    print("=== baseline GenieACS ===")
    baseline = genieacs_last_inform()
    print("lastInform", baseline)
    print("=== baseline OLT ===")
    print(olt_summary())

    # --- A) SmartOLT ---
    print("\n=== A) DELETE then AUTHORIZE via SmartOLT ===")
    try:
        olt_delete()
    except Exception as ex:
        print("olt_delete warn", ex)
    # Best-effort cloud delete (external id often = SN)
    for ext in (SN, f"gigafiber-ma5608t_{BOARD}_{PORT}_{ONT_ID}"):
        try:
            resp = smartolt_post(secrets, f"onu/delete/{urllib.parse.quote(ext, safe='')}")
            print("smartolt_delete", ext, resp)
        except Exception as ex:
            print("smartolt_delete fail", ext, type(ex).__name__, ex)

    time.sleep(3)
    before_a = genieacs_last_inform()
    auth = smartolt_post(
        secrets,
        "onu/authorize_onu",
        {
            "olt_id": OLT_SMARTOLT_ID,
            "pon_type": "gpon",
            "board": BOARD,
            "port": PORT,
            "sn": SN,
            "vlan": VLAN,
            "onu_type": "F6600RV9.0.21",
            "zone": "Zone 1",
            "name": "SMARTOLT-DUAL",
            "onu_mode": "Routing",
            "custom_profile": "Generic_1",
        },
    )
    print("smartolt_authorize", auth)
    summary_a = wait_online("SMARTOLT")
    print(summary_a)
    inform_a = wait_inform_after("SMARTOLT", before_a, timeout_s=120)
    print("SMARTOLT_RESULT", {"olt": summary_a.replace("\n", " | "), "lastInform": inform_a})

    # --- B) SSH ---
    print("\n=== B) DELETE then AUTHORIZE via SSH (Gateway CLI) ===")
    try:
        olt_delete()
    except Exception as ex:
        print("olt_delete warn", ex)
    for ext in (SN, auth.get("unique_external_id") or ""):
        if not ext:
            continue
        try:
            resp = smartolt_post(secrets, f"onu/delete/{urllib.parse.quote(str(ext), safe='')}")
            print("smartolt_delete", ext, resp)
        except Exception as ex:
            print("smartolt_delete fail", ext, type(ex).__name__)

    time.sleep(3)
    before_b = genieacs_last_inform()
    olt_authorize_ssh()
    summary_b = wait_online("SSH")
    print(summary_b)
    inform_b = wait_inform_after("SSH", before_b, timeout_s=180)
    print("SSH_RESULT", {"olt": summary_b.replace("\n", " | "), "lastInform": inform_b})

    print("\n=== SUMMARY ===")
    print(
        json.dumps(
            {
                "baseline_inform": baseline,
                "smartolt_inform": inform_a,
                "ssh_inform": inform_b,
                "smartolt_auth_status": auth.get("status"),
                "smartolt_external_id": auth.get("unique_external_id"),
            },
            indent=2,
        )
    )


if __name__ == "__main__":
    main()
