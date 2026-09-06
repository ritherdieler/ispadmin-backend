#!/usr/bin/env python3
from __future__ import annotations

import base64
import json
import os
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request


def resolve_cafile() -> str | None:
    candidates: list[str] = []
    try:
        import certifi

        candidates.append(certifi.where())
    except ImportError:
        pass
    for env_name in ("SSL_CERT_FILE", "REQUESTS_CA_BUNDLE"):
        env = os.environ.get(env_name)
        if env:
            candidates.append(env)
    candidates.extend(
        (
            "/etc/ssl/cert.pem",
            "/etc/ssl/certs/ca-certificates.crt",
            "/opt/homebrew/etc/openssl@3/cert.pem",
            "/usr/local/etc/openssl@3/cert.pem",
        )
    )
    for path in candidates:
        if path and os.path.isfile(path):
            return path
    return None


def build_ssl_context() -> ssl.SSLContext:
    cafile = resolve_cafile()
    if cafile:
        return ssl.create_default_context(cafile=cafile)
    return ssl.create_default_context()


def urlopen(req: urllib.request.Request, timeout: int = 60):
    return urllib.request.urlopen(req, context=build_ssl_context(), timeout=timeout)


def google_access_token(sa: dict) -> str:
    header = base64.urlsafe_b64encode(b'{"alg":"RS256","typ":"JWT"}').rstrip(b"=")
    now = int(time.time())
    claim = {
        "iss": sa["client_email"],
        "scope": "https://www.googleapis.com/auth/devstorage.full_control",
        "aud": "https://oauth2.googleapis.com/token",
        "iat": now,
        "exp": now + 3600,
    }
    payload = base64.urlsafe_b64encode(json.dumps(claim, separators=(",", ":")).encode()).rstrip(b"=")
    signing_input = header + b"." + payload
    with tempfile.NamedTemporaryFile("w", delete=False) as kf:
        kf.write(sa["private_key"])
        keyfile = kf.name
    try:
        sig = subprocess.check_output(["openssl", "dgst", "-sha256", "-sign", keyfile], input=signing_input)
    finally:
        os.unlink(keyfile)
    jwt = signing_input + b"." + base64.urlsafe_b64encode(sig).rstrip(b"=")
    body = urllib.parse.urlencode(
        {
            "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
            "assertion": jwt.decode(),
        }
    ).encode()
    req = urllib.request.Request("https://oauth2.googleapis.com/token", data=body, method="POST")
    req.add_header("Content-Type", "application/x-www-form-urlencoded")
    with urlopen(req, timeout=60) as resp:
        return json.load(resp)["access_token"]


def delete_object(sa_path: str, bucket: str, object_path: str) -> str:
    sa = json.load(open(sa_path))
    token = google_access_token(sa)
    encoded_obj = urllib.parse.quote(object_path, safe="")
    del_url = f"https://storage.googleapis.com/storage/v1/b/{bucket}/o/{encoded_obj}"
    req = urllib.request.Request(del_url, method="DELETE")
    req.add_header("Authorization", f"Bearer {token}")
    try:
        with urlopen(req, timeout=60) as resp:
            print("firebase_deleted", resp.status)
            return "deleted"
    except urllib.error.HTTPError as exc:
        if exc.code == 404:
            print("firebase_already_gone")
            return "gone"
        raise


def check_ssl() -> None:
    ctx = build_ssl_context()
    if ctx is None:
        raise RuntimeError("build_ssl_context returned None")
    print("SSL_OK cafile=%s" % (resolve_cafile() or "default"))


def main(argv: list[str]) -> int:
    if len(argv) == 2 and argv[1] == "--check-ssl":
        check_ssl()
        return 0
    if len(argv) != 4:
        print("usage: tr069_e2e_firebase_delete.py <sa.json> <bucket> <object>", file=sys.stderr)
        print("       tr069_e2e_firebase_delete.py --check-ssl", file=sys.stderr)
        return 2
    delete_object(argv[1], argv[2], argv[3])
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
