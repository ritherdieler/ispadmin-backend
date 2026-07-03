#!/usr/bin/env python3
from pathlib import Path
import shutil
from datetime import datetime

path = Path("/opt/gigafiber/docker-compose.yml")
text = path.read_text()
backup = path.with_suffix(f".yml.bak.{datetime.now().strftime('%Y%m%d%H%M%S')}")
shutil.copy(path, backup)

if "TZ: America/Lima" not in text:
    text = text.replace(
        "    environment:\n      MYSQL_ROOT_PASSWORD:",
        "    environment:\n      TZ: America/Lima\n      MYSQL_ROOT_PASSWORD:",
    )
    text = text.replace(
        "    environment:\n      SPRING_PROFILES_ACTIVE:",
        "    environment:\n      TZ: America/Lima\n      SPRING_PROFILES_ACTIVE:",
    )

if "-Duser.timezone=America/Lima" not in text:
    text = text.replace(
        'CATALINA_OPTS: "-DPYTORCH_VERSION=2.7.1',
        'CATALINA_OPTS: "-Duser.timezone=America/Lima -DPYTORCH_VERSION=2.7.1',
    )

path.write_text(text)
print(f"Updated {path}, backup: {backup}")
