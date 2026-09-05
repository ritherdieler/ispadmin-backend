#!/usr/bin/env python3
import sys
from pathlib import Path


def extract_service_block(lines, service_name):
    header = f"  {service_name}:"
    start = None
    for i, line in enumerate(lines):
        if line.rstrip("\n") == header:
            start = i
            break
    if start is None:
        return None, None, None
    end = len(lines)
    for i in range(start + 1, len(lines)):
        raw = lines[i]
        stripped = raw.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if raw.startswith("  ") and not raw.startswith("    ") and stripped.endswith(":"):
            end = i
            break
        if not raw.startswith(" ") and stripped.endswith(":"):
            end = i
            break
    return start, end, lines[start:end]


def rewrite_staging_block(block):
    out = []
    has_ports = False
    for line in block:
        if line.rstrip("\n") == "  tomcat:":
            nl = "\n" if line.endswith("\n") else ""
            out.append(f"  tomcat-staging:{nl}")
            continue
        if "container_name:" in line:
            indent = line[: len(line) - len(line.lstrip())]
            nl = "\n" if line.endswith("\n") else ""
            out.append(f"{indent}container_name: tomcat-staging{nl}")
            continue
        if "ports:" in line and line.lstrip().startswith("ports:"):
            has_ports = True
        if "8080:8080" in line:
            out.append(line.replace("8080:8080", "8081:8080"))
            continue
        out.append(line)
    if not has_ports:
        nl = "\n"
        out.append(f"    ports:{nl}")
        out.append(f'      - "8081:8080"{nl}')
    return out


def ensure(path: Path) -> str:
    text = path.read_text()
    if "\n  tomcat-staging:" in "\n" + text:
        return "already"
    lines = text.splitlines(keepends=True)
    start, end, block = extract_service_block(lines, "tomcat")
    if block is None:
        raise SystemExit("tomcat service not found in compose file")
    staging = rewrite_staging_block(block)
    new_lines = lines[:end]
    if new_lines and not new_lines[-1].endswith("\n"):
        new_lines[-1] = new_lines[-1] + "\n"
    new_lines.append("\n")
    new_lines.extend(staging)
    if staging and not staging[-1].endswith("\n"):
        new_lines.append("\n")
    new_lines.extend(lines[end:])
    path.write_text("".join(new_lines))
    return "added"


if __name__ == "__main__":
    if len(sys.argv) < 2:
        raise SystemExit("usage: ensure-tomcat-staging-compose.py COMPOSE_YML")
    print(ensure(Path(sys.argv[1])))
