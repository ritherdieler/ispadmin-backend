# GenieACS en red interna (sin túnel)

GenieACS escucha en todas las interfaces del host (`0.0.0.0`). Los ONUs deben usar una **IP interna alcanzable desde su WAN de gestión**, no un túnel público.

## Arranque

```powershell
docker compose -f docker-compose.genieacs.yml up -d
```

No uses `--profile public` en operación normal.

## URLs

| Servicio | Puerto | Uso |
|---|---|---|
| CWMP | 7547 | URL TR-069 en la ONU: `http://<IP_INTERNA>:7547/` |
| NBI | 7557 | Spring Boot (`GENIEACS_NBI_URL`) |
| UI | 3001 | Panel web GenieACS |

Comprobar endpoints en esta máquina:

```powershell
.\scripts\genieacs-internal-network.ps1
```

## Firewall (Windows)

Reglas inbound TCP recomendadas (perfil Private/Any):

- `GenieACS CWMP 7547`
- `GenieACS NBI 7557`
- `GenieACS UI 3001`

PowerShell como Administrador:

```powershell
foreach ($entry in @(
  @{ Name = 'GenieACS CWMP 7547'; Port = 7547 },
  @{ Name = 'GenieACS NBI 7557'; Port = 7557 },
  @{ Name = 'GenieACS UI 3001'; Port = 3001 }
)) {
  if (-not (Get-NetFirewallRule -DisplayName $entry.Name -ErrorAction SilentlyContinue)) {
    New-NetFirewallRule -DisplayName $entry.Name -Direction Inbound -Action Allow -Protocol TCP -LocalPort $entry.Port -Profile Any
  }
}
```

## ONU V-SOL (mínimo)

1. **Enable TR-069** ON
2. **Server URL** → IP interna del ACS (ej. VLAN gestión `192.168.30.x`, no la IP Wi-Fi del técnico si el Inform sale por WAN)
3. **WAN ServiceMode** → `TR069_INTERNET` (sin esto: `ACS not set`)
4. **Periodic Inform** ON (300 s prod)

La IP/gateway/VLAN de la WAN de gestión suele crearla la **OLT** al autorizar la ONU; no hace falta escribirla a mano en cada instalación si el perfil OLT ya la provisiona.

## Apagar túnel temporal (bore)

```powershell
docker rm -f genieacs-cwmp-tunnel
docker compose -f docker-compose.genieacs.yml --profile public down
```

## Spring Boot

En `application-dev.properties` / prod:

```properties
GENIEACS_NBI_URL=http://<IP_INTERNA_ACS>:7557
```

Si Spring corre en el mismo host que GenieACS: `http://localhost:7557`.
