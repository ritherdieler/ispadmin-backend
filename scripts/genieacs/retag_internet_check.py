import os
import re
import subprocess
import sys
import time

TR069_NETS = (("192.168.252.0", 22), ("10.20.0.0", 22))
WAN_LEAF = re.compile(
    r"(InternetGatewayDevice\.WANDevice\.\d+\.WANConnectionDevice\.\d+\.WAN(?:IP|PPP)Connection\.\d+)\."
    r"(ExternalIPAddress|ConnectionStatus|X_CT-COM_VLANIDMark|X_ZTE-COM_VLANID)$"
)


def ip_to_int(ip):
    parts = str(ip).split(".")
    if len(parts) != 4:
        return None
    n = 0
    for part in parts:
        try:
            octet = int(part)
        except ValueError:
            return None
        if octet < 0 or octet > 255:
            return None
        n = (n << 8) + octet
    return n & 0xFFFFFFFF


def in_cidr(ip, base, prefix):
    addr = ip_to_int(ip)
    net = ip_to_int(base)
    if addr is None or net is None:
        return False
    mask = 0 if prefix == 0 else (0xFFFFFFFF << (32 - prefix)) & 0xFFFFFFFF
    return (addr & mask) == (net & mask)


def is_tr069_ip(ip):
    return any(in_cidr(ip, base, prefix) for base, prefix in TR069_NETS)


def flatten(node, prefix=""):
    rows = []
    if not isinstance(node, dict):
        return rows
    if "_value" in node:
        rows.append((prefix.rstrip("."), node.get("_value")))
    for key, child in node.items():
        if str(key).startswith("_"):
            continue
        rows.extend(flatten(child, prefix + key + "."))
    return rows


def classify_wans(device):
    grouped = {}
    for path, value in flatten(device):
        match = WAN_LEAF.search(str(path))
        if not match:
            continue
        wan = match.group(1)
        leaf = match.group(2)
        row = grouped.setdefault(wan, {"path": wan, "ip": "", "status": "", "vlan": ""})
        text = "" if value is None else str(value)
        if leaf == "ExternalIPAddress":
            row["ip"] = text
        elif leaf == "ConnectionStatus":
            row["status"] = text
        elif row["vlan"] == "":
            row["vlan"] = text
    wans = list(grouped.values())
    internet = [w for w in wans if w["ip"] and not is_tr069_ip(w["ip"])]
    tr069 = [w for w in wans if w["ip"] and is_tr069_ip(w["ip"])]
    return {"wans": wans, "internet": internet, "tr069": tr069}


def ping_received(output):
    match = re.search(r"(\d+)\s+packets transmitted,\s+(\d+)\s+(?:packets\s+)?received", str(output), re.I)
    if not match:
        return 0
    return int(match.group(2))


def ping_transport_failed(output):
    text = str(output or "")
    return "Permission denied" in text or "Connection refused" in text


def internet_lost(before_up, after_up):
    return bool(before_up) and not bool(after_up)


def _ping_once(ip):
    host = os.environ.get("VPS_HOST", "").strip()
    user = os.environ.get("VPS_USER", "root").strip() or "root"
    port = os.environ.get("VPS_PORT", "22").strip() or "22"
    remote = "ping -c 3 -W 2 %s" % ip
    if host:
        cmd = [
            "sshpass",
            "-e",
            "ssh",
            "-o",
            "StrictHostKeyChecking=no",
            "-o",
            "PreferredAuthentications=password",
            "-o",
            "PubkeyAuthentication=no",
            "-p",
            port,
            "%s@%s" % (user, host),
            remote,
        ]
    else:
        cmd = ["ping", "-c", "3", "-W", "2", ip]
    try:
        proc = subprocess.run(cmd, capture_output=True, text=True, timeout=25)
    except (OSError, subprocess.TimeoutExpired) as exc:
        return False, str(exc)
    blob = (proc.stdout or "") + "\n" + (proc.stderr or "")
    return ping_received(blob) > 0, blob.strip()[-400:]


def ping_ip(ip):
    last = ""
    for _ in range(4):
        ok, blob = _ping_once(ip)
        last = blob
        if ping_transport_failed(blob):
            time.sleep(2)
            continue
        return ok, blob
    raise SystemExit("VPS ping SSH failed after retries: %s" % last[-200:])


def snapshot_label(tag, classified, reachable, ping_out):
    internet = classified["internet"][0] if classified["internet"] else {}
    tr069 = classified["tr069"][0] if classified["tr069"] else {}
    return (
        "%s internet_ip=%s internet_vlan=%s internet_status=%s reachable=%s "
        "tr069_ip=%s tr069_vlan=%s ping=%s"
        % (
            tag,
            internet.get("ip") or "-",
            internet.get("vlan") or "-",
            internet.get("status") or "-",
            "yes" if reachable else "no",
            tr069.get("ip") or "-",
            tr069.get("vlan") or "-",
            (ping_out or "").replace("\n", " ")[:180],
        )
    )


def check_not_lost(before_up, after_up, stage):
    if internet_lost(before_up, after_up):
        raise SystemExit(
            "internet was reachable and became unreachable after %s; aborting CPE retag" % stage
        )


def _self_test():
    device = {
        "InternetGatewayDevice": {
            "WANDevice": {
                "1": {
                    "WANConnectionDevice": {
                        "1": {
                            "WANIPConnection": {
                                "1": {
                                    "ExternalIPAddress": {"_value": "192.168.211.86"},
                                    "ConnectionStatus": {"_value": "Connected"},
                                    "X_CT-COM_VLANIDMark": {"_value": "1"},
                                }
                            }
                        },
                        "2": {
                            "WANIPConnection": {
                                "1": {
                                    "ExternalIPAddress": {"_value": "192.168.254.10"},
                                    "ConnectionStatus": {"_value": "Connected"},
                                    "X_CT-COM_VLANIDMark": {"_value": "100"},
                                }
                            }
                        },
                    }
                }
            }
        }
    }
    classified = classify_wans(device)
    assert classified["internet"][0]["ip"] == "192.168.211.86"
    assert classified["internet"][0]["vlan"] == "1"
    assert classified["tr069"][0]["ip"] == "192.168.254.10"
    assert classified["tr069"][0]["vlan"] == "100"
    assert ping_received("3 packets transmitted, 3 received, 0% packet loss") == 3
    assert ping_received("3 packets transmitted, 0 received, 100% packet loss") == 0
    assert internet_lost(True, False) is True
    assert internet_lost(False, False) is False
    assert internet_lost(True, True) is False
    assert ping_transport_failed("Permission denied, please try again.") is True
    assert ping_transport_failed("3 packets transmitted, 3 received") is False
    print("retag_internet_check self-test ok")


if __name__ == "__main__":
    if "--self-test" in sys.argv:
        _self_test()
        sys.exit(0)
    print("usage: retag_internet_check.py --self-test", file=sys.stderr)
    sys.exit(2)
