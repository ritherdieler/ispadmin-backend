#!/usr/bin/env python3
import sys
from pathlib import Path

PROFILE_KEY = "SPRING_PROFILES_ACTIVE"
PROFILE_VALUE = "prod,staging"
SATELLITE_PASSWORD_KEYS = (
    "ACS_DATASOURCE_PASSWORD",
    "OLTGATEWAY_DATASOURCE_PASSWORD",
    "TRAFFIC_DATASOURCE_PASSWORD",
)
SPRING_PASSWORD_KEY = "SPRING_DATASOURCE_PASSWORD"


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


def ensure_spring_profiles(block):
    out = []
    has_env = False
    has_profile = False
    changed = False
    for line in block:
        if line.strip() == "environment:" or line.lstrip().startswith("environment:"):
            has_env = True
        if PROFILE_KEY in line:
            has_profile = True
            indent = line[: len(line) - len(line.lstrip())]
            nl = "\n" if line.endswith("\n") else ""
            if line.lstrip().startswith("-"):
                new = f"{indent}- {PROFILE_KEY}={PROFILE_VALUE}{nl}"
            else:
                new = f"{indent}{PROFILE_KEY}: {PROFILE_VALUE}{nl}"
            if line != new:
                changed = True
            out.append(new)
            continue
        out.append(line)
    if has_profile:
        return out, changed
    nl = "\n"
    inserted = []
    if has_env:
        for line in out:
            inserted.append(line)
            if line.strip() == "environment:" or line.lstrip().startswith("environment:"):
                indent = line[: len(line) - len(line.lstrip())]
                inserted.append(f"{indent}  {PROFILE_KEY}: {PROFILE_VALUE}{nl}")
                changed = True
        return inserted, True
    inserted.extend(out)
    inserted.append(f"    environment:{nl}")
    inserted.append(f"      {PROFILE_KEY}: {PROFILE_VALUE}{nl}")
    return inserted, True


def spring_password_rhs(block):
    for line in block:
        stripped = line.strip()
        if stripped.startswith("-") and SPRING_PASSWORD_KEY in stripped:
            _, _, rhs = stripped.partition("=")
            if stripped.lstrip("- ").startswith(SPRING_PASSWORD_KEY + "="):
                return rhs
        if not stripped.startswith("#") and SPRING_PASSWORD_KEY in stripped and ":" in stripped:
            key, _, rhs = stripped.partition(":")
            if key.strip() == SPRING_PASSWORD_KEY:
                return rhs.strip()
    return "${" + SPRING_PASSWORD_KEY + "}"


def env_item_indent_and_style(block):
    for line in block:
        if PROFILE_KEY in line or SPRING_PASSWORD_KEY in line:
            indent = line[: len(line) - len(line.lstrip())]
            if line.lstrip().startswith("-"):
                return indent, "list"
            return indent, "map"
    return "      ", "map"


def ensure_satellite_passwords(block):
    rhs = spring_password_rhs(block)
    missing = [key for key in SATELLITE_PASSWORD_KEYS if not any(key in line for line in block)]
    if not missing:
        return block, False
    indent, style = env_item_indent_and_style(block)
    nl = "\n"
    inserts = []
    for key in missing:
        if style == "list":
            inserts.append(f"{indent}- {key}={rhs}{nl}")
        else:
            inserts.append(f"{indent}{key}: {rhs}{nl}")
    out = []
    inserted = False
    for line in block:
        out.append(line)
        if not inserted and PROFILE_KEY in line:
            out.extend(inserts)
            inserted = True
    if not inserted:
        out.extend(inserts)
    return out, True


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
    profiled, _ = ensure_spring_profiles(out)
    return ensure_satellite_passwords(profiled)[0]


def ensure(path: Path) -> str:
    text = path.read_text()
    lines = text.splitlines(keepends=True)
    start, end, block = extract_service_block(lines, "tomcat-staging")
    if block is not None:
        new_block, changed = ensure_spring_profiles(block)
        new_block, pass_changed = ensure_satellite_passwords(new_block)
        if changed or pass_changed:
            path.write_text("".join(lines[:start] + new_block + lines[end:]))
            return "updated"
        return "already"
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
