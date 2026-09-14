from __future__ import annotations


def ensure_tomcat_env_file(compose_text: str, env_file_path: str = "/opt/gigafiber/.env") -> str:
    if "env_file:" in compose_text and env_file_path in compose_text:
        return compose_text

    lines = compose_text.splitlines()
    out: list[str] = []
    in_tomcat = False
    inserted = False

    for line in lines:
        if line.rstrip() == "  tomcat:":
            in_tomcat = True
            out.append(line)
            continue

        if in_tomcat and line.startswith("  ") and not line.startswith("    ") and line.rstrip().endswith(":"):
            in_tomcat = False

        if in_tomcat and not inserted and line.strip() == "restart: unless-stopped":
            out.append(line)
            out.append("    env_file:")
            out.append(f"      - {env_file_path}")
            inserted = True
            continue

        out.append(line)

    if not inserted:
        raise ValueError("Could not insert env_file under tomcat (missing restart: unless-stopped)")

    return "\n".join(out) + ("\n" if compose_text.endswith("\n") or compose_text == "" else "")


def main() -> None:
    import argparse
    from pathlib import Path

    parser = argparse.ArgumentParser(description="Ensure tomcat.env_file in docker-compose.yml")
    parser.add_argument("compose_path")
    parser.add_argument("--env-file", default="/opt/gigafiber/.env")
    args = parser.parse_args()

    path = Path(args.compose_path)
    original = path.read_text()
    updated = ensure_tomcat_env_file(original, args.env_file)
    if updated != original:
        path.write_text(updated)
        print(f"Added env_file: {args.env_file}")
    else:
        print("env_file already present")


if __name__ == "__main__":
    main()
