#!/usr/bin/env python3
import re
import sys
from pathlib import Path
from typing import Optional

UPSTREAM = """
upstream gigafiber_backend_staging {
    server 127.0.0.1:8081;
}
"""


def insert_upstream(text: str) -> str:
    if "upstream gigafiber_backend_staging" in text:
        return text
    match = re.search(r"upstream\s+gigafiber_backend\s*\{[^}]*\}", text)
    if not match:
        raise SystemExit("upstream gigafiber_backend not found")
    return text[: match.end()] + "\n" + UPSTREAM + text[match.end() :]


def retarget_staging_locations(text: str) -> str:
    lines = text.splitlines(keepends=True)
    out = []
    in_loc = False
    brace = 0
    for line in lines:
        if not in_loc and re.search(r"location\s+/ispadmin-staging", line):
            in_loc = True
            brace = line.count("{") - line.count("}")
            line = line.replace(
                "proxy_pass http://gigafiber_backend;",
                "proxy_pass http://gigafiber_backend_staging;",
            )
            out.append(line)
            if brace <= 0:
                in_loc = False
            continue
        if in_loc:
            if (
                "proxy_pass http://gigafiber_backend;" in line
                and "gigafiber_backend_staging" not in line
            ):
                line = line.replace(
                    "proxy_pass http://gigafiber_backend;",
                    "proxy_pass http://gigafiber_backend_staging;",
                )
            brace += line.count("{") - line.count("}")
            if brace <= 0:
                in_loc = False
        out.append(line)
    return "".join(out)


def insert_snippet_if_missing(text: str, snippet: str) -> str:
    if "location /ispadmin-staging/" in text:
        return text
    marker = "    location /ispadmin/ws {"
    idx = text.find(marker)
    if idx == -1:
        raise SystemExit("Could not find location /ispadmin/ws in nginx conf")
    end = text.find("    location / {", idx)
    if end == -1:
        raise SystemExit("Could not find location / after websocket block")
    return text[:end] + snippet.rstrip() + "\n\n" + text[end:]


def rewrite(conf_path: Path, snippet_path: Optional[Path]) -> str:
    text = conf_path.read_text()
    text = insert_upstream(text)
    snippet = snippet_path.read_text() if snippet_path else ""
    if snippet:
        text = insert_snippet_if_missing(text, snippet)
    text = retarget_staging_locations(text)
    conf_path.write_text(text)
    return "updated"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("usage: rewrite-nginx-staging-upstream.py CONF [SNIPPET]")
    snippet = Path(sys.argv[2]) if len(sys.argv) > 2 else None
    print(rewrite(Path(sys.argv[1]), snippet))
