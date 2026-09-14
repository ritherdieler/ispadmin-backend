#!/usr/bin/env python3
import argparse
import os
import socket
import sys


def port_open(host: str, port: int, timeout: float = 5.0) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except OSError:
        return False


def test_api(host: str, username: str, password: str) -> dict:
    if not port_open(host, 8728):
        return {"host": host, "api_port_open": False, "status": "BLOCKED"}

    try:
        from librouteros import connect
    except ImportError:
        return {
            "host": host,
            "api_port_open": True,
            "status": "ERROR",
            "detail": "pip install librouteros",
        }

    try:
        api = connect(username=username, password=password, host=host, port=8728, timeout=15)
        identity = list(api("/system/identity/print"))[0]["name"]
        resource = list(api("/system/resource/print"))[0]
        services = list(api("/ip/service/print"))
        api.close()
        api_svc = next((s for s in services if s.get("name") == "api"), {})
        return {
            "host": host,
            "api_port_open": True,
            "status": "OK",
            "identity": identity,
            "version": resource.get("version"),
            "board": resource.get("board-name"),
            "api_disabled": api_svc.get("disabled", "?"),
            "api_port": api_svc.get("port", "?"),
        }
    except Exception as exc:
        return {
            "host": host,
            "api_port_open": True,
            "status": "LOGIN_FAILED",
            "detail": str(exc),
        }


def main() -> int:
    parser = argparse.ArgumentParser(description="Verificación Fase 1 MikroTik MK2 (38.224.231.4)")
    parser.add_argument("--host", default="38.224.231.4")
    parser.add_argument("--username", default=os.environ.get("MIKROTIK_USERNAME", "gigafiber2023"))
    parser.add_argument("--password", default=os.environ.get("MIKROTIK_PASSWORD", ""))
    parser.add_argument("--compare-host", default="38.224.231.2")
    args = parser.parse_args()

    if not args.password:
        print("Defina MIKROTIK_PASSWORD o --password", file=sys.stderr)
        return 1

    print("=== Puertos ===")
    for port in (8291, 8728, 8729, 22):
        ok = port_open(args.host, port)
        print(f"  {args.host}:{port} {'OK' if ok else 'cerrado'}")

    print("\n=== Referencia MK1 ===")
    ref = test_api(args.compare_host, args.username, args.password)
    print(ref)

    print("\n=== MK2 objetivo ===")
    result = test_api(args.host, args.username, args.password)
    print(result)

    if result.get("status") == "OK":
        print("\nFase 1.1 OK: API accesible. Probar backend:")
        print("  GET /networkDevice/connection/8/system-info")
        return 0

    print("\nPendiente: habilitar API en Winbox (ver .agent-docs/mikrotik-mk2-fase1-runbook.md)")
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
