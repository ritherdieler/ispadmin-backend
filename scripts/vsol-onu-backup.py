#!/usr/bin/env python3
"""Download VSOL / ZTE-OEM ONU configuration backup (F6600R-style web UI).

Usage:
  python3 scripts/vsol-onu-backup.py --host 192.168.1.1 --user admin --password 'YOUR_PASSWORD'

Requires LAN access to the ONU. Saves a .bin backup under ./local-docs/onu-backups/.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path

import requests

BACKUP_TAG_CANDIDATES = (
    "backupAndRestore",
    "configBackup",
    "dataBackup",
    "backupMgr",
    "usrCfgMgr",
    "configMgr",
    "devMgr",
)

DOWNLOAD_HINTS = re.compile(
    r"(backup|restore|download|export|cfg|config file|save config)",
    re.I,
)


def sha256_login_password(clear_password: str, login_token: str) -> str:
    return hashlib.sha256(f"{clear_password}{login_token}".encode()).hexdigest()


class OnuWebClient:
    def __init__(self, host: str) -> None:
        self.base = host.rstrip("/")
        self.session = requests.Session()

    def _url(self, query: str) -> str:
        return f"{self.base}/?{query}"

    def session_token(self) -> str:
        response = self.session.get(
            self._url("_type=loginData&_tag=login_entry"), timeout=15
        )
        response.raise_for_status()
        return response.json()["sess_token"]

    def login_token(self) -> str:
        response = self.session.get(
            self._url("_type=loginData&_tag=login_token"), timeout=15
        )
        response.raise_for_status()
        match = re.search(r"<[^>]+>([^<]+)</", response.text)
        if not match:
            raise RuntimeError("Could not parse login token XML")
        return match.group(1)

    def login(self, username: str, password: str) -> None:
        sess = self.session_token()
        token = self.login_token()
        payload = {
            "action": "login",
            "Username": username,
            "Password": sha256_login_password(password, token),
            "_sessionTOKEN": sess,
        }
        response = self.session.post(
            self._url("_type=loginData&_tag=login_entry"), data=payload, timeout=15
        )
        response.raise_for_status()
        body = response.json()
        if not body.get("login_need_refresh"):
            raise RuntimeError(
                f"Login failed: {body.get('loginErrMsg') or body.get('promptMsg') or body}"
            )

    def fetch_menu_page(self, tag: str) -> str:
        response = self.session.get(
            self._url(f"_type=menuView&_tag={tag}"), timeout=20
        )
        response.raise_for_status()
        return response.text

    def discover_backup_tags(self) -> list[str]:
        found: list[str] = []
        menu_html = self.fetch_menu_page("menu_api")
        for tag in BACKUP_TAG_CANDIDATES:
            if tag in menu_html:
                found.append(tag)
        for match in re.finditer(r'"id"\s*:\s*"([^"]+)"', menu_html):
            tag = match.group(1)
            if any(h in tag.lower() for h in ("backup", "restore", "config", "cfg")):
                if tag not in found:
                    found.append(tag)
        return found

    def csrf_mask(self) -> str:
        page = self.fetch_menu_page("Management_Device_control.js")
        match = re.search(r'name="csrfMask"\s+value="([^"]+)"', page)
        if match:
            return match.group(1)
        raise RuntimeError("csrfMask not found (login required?)")

    def probe_download_urls(self) -> list[tuple[str, int, str]]:
        candidates = [
            "/boaform/getASPdata/formMgmConfig",
            "/?_type=downloadData&_tag=backupcfg",
            "/?_type=downloadData&_tag=backup",
            "/?_type=downloadData&_tag=config",
            "/?_type=downloadData&_tag=usrconfig",
            "/?_type=downloadData&_tag=devconfig",
            "/?_type=downloadData&_tag=ConfigBackup",
            "/?_type=downloadData&_tag=BackupCfg",
            "/download.cfg",
            "/config.bin",
            "/backup.bin",
        ]
        results: list[tuple[str, int, str]] = []
        for path in candidates:
            url = f"{self.base}{path}"
            response = self.session.get(url, timeout=15, stream=True)
            ctype = response.headers.get("content-type", "")
            results.append((path, response.status_code, ctype))
        return results

    def download_first_backup(self, output_dir: Path, serial_hint: str) -> Path:
        output_dir.mkdir(parents=True, exist_ok=True)
        timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        safe_serial = re.sub(r"[^A-Za-z0-9_-]+", "_", serial_hint or "onu")
        output_path = output_dir / f"vsol-backup-{safe_serial}-{timestamp}.bin"

        csrf = self.csrf_mask()
        boa_url = f"{self.base}/boaform/getASPdata/formMgmConfig"
        response = self.session.post(
            boa_url,
            data={"csrfMask": csrf},
            timeout=30,
            headers={"Content-Type": "application/x-www-form-urlencoded"},
        )
        if response.content and len(response.content) > 256:
            output_path.write_bytes(response.content)
            return output_path

        for path in [
            "/?_type=downloadData&_tag=backupcfg",
            "/?_type=downloadData&_tag=backup",
            "/?_type=downloadData&_tag=config",
            "/?_type=downloadData&_tag=usrconfig",
        ]:
            url = f"{self.base}{path}"
            response = self.session.get(url, timeout=30)
            if response.status_code == 200 and len(response.content) > 256:
                output_path.write_bytes(response.content)
                return output_path

        raise RuntimeError(
            "No backup file returned by known download URLs. "
            "Use the web UI: Management & Diagnosis → Backup & Restore → Download."
        )


def main() -> int:
    parser = argparse.ArgumentParser(description="Download VSOL ONU config backup")
    parser.add_argument("--host", default="http://192.168.1.1")
    parser.add_argument("--user", required=True)
    parser.add_argument("--password", required=True)
    parser.add_argument(
        "--output-dir",
        default=str(Path(__file__).resolve().parents[1] / "local-docs" / "onu-backups"),
    )
    parser.add_argument("--probe-only", action="store_true")
    args = parser.parse_args()

    client = OnuWebClient(args.host)
    client.login(args.user, args.password)

    tags = client.discover_backup_tags()
    print("Backup-related menu tags:", tags or "(none found in menu_api)")

    probes = client.probe_download_urls()
    print("Download URL probe:")
    for path, status, ctype in probes:
        print(f"  {path} -> HTTP {status} ({ctype})")

    if args.probe_only:
        return 0

    out = client.download_first_backup(Path(args.output_dir), serial_hint="VSOL")
    print(f"Backup saved: {out} ({out.stat().st_size} bytes)")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:  # noqa: BLE001 - CLI tool
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(1)
