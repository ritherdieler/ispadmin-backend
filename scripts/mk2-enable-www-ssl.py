#!/usr/bin/env python3
from __future__ import annotations

import argparse
import os
import subprocess
import sys

VPS_ALLOW = "212.85.13.47/32,192.168.0.0/16"

ROS_SCRIPT = r"""
/certificate remove [find where name~"^netdiag"]
/certificate add name=netdiag-ca common-name=GigaFiber-CA-MK2 organization=GigaFiber country=PE key-size=2048 days-valid=3650 key-usage=key-cert-sign,crl-sign
:delay 2
/certificate sign netdiag-ca
:delay 3
/certificate add name=netdiag-rest-mk2 common-name={host} organization=GigaFiber country=PE key-size=2048 days-valid=3650 key-usage=tls-server subject-alt-name=IP:{host}
:delay 2
/certificate sign netdiag-rest-mk2 ca=netdiag-ca
:delay 3
/ip service set [find name=www-ssl] certificate=netdiag-rest-mk2 disabled=no port=443 tls-version=only-1.2 address={allow}
/certificate print detail where name~"^netdiag"
/ip service print detail where name=www-ssl
"""


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default=os.environ.get("ROUTEROS_MK2_HOST", "38.224.231.4"))
    parser.add_argument("--user", default=os.environ.get("ROUTEROS_MK2_USER"))
    parser.add_argument("--password", default=os.environ.get("ROUTEROS_MK2_PASSWORD"))
    parser.add_argument("--ssh-port", type=int, default=int(os.environ.get("ROUTEROS_MK2_SSH_PORT", "22")))
    parser.add_argument("--allow-address", default=os.environ.get("ROUTEROS_MK2_WWW_SSL_ALLOW", VPS_ALLOW))
    args = parser.parse_args()
    if not args.user or not args.password:
        print("ROUTEROS_MK2_USER/PASSWORD required", file=sys.stderr)
        return 1

    script = ROS_SCRIPT.format(host=args.host, allow=args.allow_address)
    env = os.environ.copy()
    env["SSHPASS"] = args.password
    cmd = [
        "sshpass",
        "-e",
        "ssh",
        "-o",
        "StrictHostKeyChecking=no",
        "-p",
        str(args.ssh_port),
        f"{args.user}@{args.host}",
    ]
    result = subprocess.run(cmd, input=script, text=True, capture_output=True, env=env)
    sys.stdout.write(result.stdout)
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        return result.returncode
    if "certificate=netdiag-rest-mk2" not in result.stdout:
        print("www-ssl certificate assignment not confirmed in output", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
