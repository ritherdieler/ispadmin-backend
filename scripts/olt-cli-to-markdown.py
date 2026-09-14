#!/usr/bin/env python3
import re
import sys
from pathlib import Path

RAW = Path("/tmp/olt-cli-explore-raw.txt")
OUT = Path(__file__).resolve().parent.parent / ".agent-docs" / "olt-ma5608t-cli-explore-output.md"

ANSI = re.compile(r"\x1b\[[0-9;?]*[A-Za-z]")
CONTROL = re.compile(r"[\x00-\x08\x0b-\x0c\x0e-\x1f\x7f]")


def clean(text: str) -> str:
    text = ANSI.sub("", text)
    text = CONTROL.sub("", text)
    text = re.sub(r"\r", "", text)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def parse_commands(block: str) -> list[str]:
    lines = []
    for line in block.splitlines():
        line = line.strip()
        if not line or line.startswith("---") or line.startswith("Command of"):
            continue
        if line.startswith("%") or line.startswith("Warning:"):
            continue
        if line.startswith("MA5608T"):
            continue
        if line.startswith("Command:"):
            continue
        m = re.match(r"^([a-z0-9][a-z0-9\-]*(?:\s+[a-z0-9\-]+)?)\s+(.+)$", line, re.I)
        if m:
            cmd = m.group(1).strip()
            desc = m.group(2).strip()
            if cmd.endswith("?"):
                cmd = cmd[:-1]
            lines.append(f"- `{cmd}` — {desc}")
        elif re.match(r"^[a-z0-9\-]+(\s+[a-z0-9\-]+)?$", line, re.I):
            lines.append(f"- `{line}`")
        elif re.match(r"^\{.*\}:$", line):
            lines.append(f"- Argumentos: `{line}`")
    return lines


def main() -> int:
    if not RAW.exists():
        print(f"Missing {RAW}", file=sys.stderr)
        return 1

    raw = clean(RAW.read_text(encoding="utf-8", errors="replace"))
    sections = re.split(r"========== (.+?) ==========", raw)
    parsed = []
    for i in range(1, len(sections), 2):
        title = sections[i].strip()
        body = sections[i + 1] if i + 1 < len(sections) else ""
        cmds = parse_commands(body)
        parsed.append((title, body, cmds))

    md = [
        "# Referencia CLI — Huawei MA5608T",
        "",
        "Documento generado automaticamente desde ayuda contextual (`?`) de la OLT.",
        "",
        "| Campo | Valor |",
        "|-------|-------|",
        "| Modelo | MA5608T |",
        "| Firmware | MA5600V800R015C00 |",
        "| IP gestion | 10.11.104.2 |",
        "| Sesion | Una sola conexion SSH |",
        "",
        "## Modos de operacion",
        "",
        "| Prompt | Modo |",
        "|--------|------|",
        "| `MA5608T>` | Usuario |",
        "| `MA5608T#` | Privilegiado (enable) |",
        "| `MA5608T(config)#` | Configuracion global |",
        "| `MA5608T(config-if-gpon-0/0)#` | Interfaz GPON slot 0 |",
        "| `MA5608T(config-gpon-lineprofile-N)#` | Perfil de linea GPON |",
        "| `MA5608T(config-gpon-srvprofile-N)#` | Perfil de servicio GPON |",
        "",
        "## Notas de sintaxis Huawei",
        "",
        "- `<K>` = keyword obligatorio",
        "- `<U>` = valor numerico",
        "- `<S>` = string",
        "- `{ a|b }` = elegir uno",
        "- `[opt]` = opcional",
        "- Usar `screen-length 0 temporary` para evitar paginacion en `display`",
        "",
        "## Comandos clave para reemplazo SmartOLT",
        "",
        "| Operacion SmartOLT | Comandos OLT |",
        "|--------------------|--------------|",
        "| ONUs no configuradas | `display ont autofind all` |",
        "| Detalle ONU por SN | `display ont info by-sn <sn> all` |",
        "| Autorizar ONU | `interface gpon 0/X` → `ont confirm <port> sn-auth \"<sn>\" omci ...` |",
        "| Eliminar ONU | `ont delete <port> <ont-id>` |",
        "| Reiniciar ONU | `ont reset <port> <ont-id>` |",
        "| Perfiles | `display ont-lineprofile gpon all`, `display ont-srvprofile gpon all` |",
        "| Service port / VLAN | `service-port ...`, `display service-port all` |",
        "| TR-069 | `ont-tr069-server-profile`, `ont tr069-server-config`, line profile `tr069-management enable` |",
        "",
    ]

    current_mode = None
    mode_map = {
        "USER MODE": "Modo usuario (`MA5608T>`)",
        "ENABLE MODE": "Modo privilegiado (`MA5608T#`)",
        "CONFIG MODE": "Modo configuracion (`MA5608T(config)#`)",
        "GPON 0/0": "Interfaz GPON 0/0",
        "LINE PROFILE": "Perfil de linea GPON",
        "SRV PROFILE": "Perfil de servicio GPON",
    }

    for title, body, cmds in parsed:
        if title == "CONNECT - single SSH session":
            continue
        mode_key = next((k for k in mode_map if k in title), None)
        if mode_key and mode_key != current_mode:
            current_mode = mode_key
            md.extend(["", f"## {mode_map[mode_key]}", ""])

        md.append(f"### {title}")
        md.append("")
        if cmds:
            md.extend(cmds)
        else:
            snippet = "\n".join(body.splitlines()[:40]).strip()
            if snippet:
                md.append("```text")
                md.append(snippet)
                md.append("```")
            else:
                md.append("_Sin salida capturada._")
        md.append("")

    md.extend([
        "## Acceso SSH desde PC (legacy crypto)",
        "",
        "```bash",
        "ssh -o KexAlgorithms=diffie-hellman-group-exchange-sha1 \\",
        "    -o HostKeyAlgorithms=ssh-rsa \\",
        "    -o PubkeyAcceptedKeyTypes=ssh-rsa \\",
        "    -o Ciphers=aes128-cbc \\",
        "    root@10.11.104.2",
        "```",
        "",
        "## Regenerar esta documentacion",
        "",
        "```bash",
        "scripts/olt-cli-explore.expect",
        "python3 scripts/olt-cli-to-markdown.py",
        "```",
        "",
    ])

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(md), encoding="utf-8")
    print(f"Written {OUT} ({len(md)} lines)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
