# Ficha: General + VPN/TR069

## General `/general`

Captura 2026-07-17.

| Setting | Value observado |
|---------|-----------------|
| Title | SMARTOLT |
| Timezone | America/Lima |
| IPs allowed | Allowed from anywhere |
| Time limit for installers to see ONUs (days) | 5 |
| Login screen language | English |

Sub-nav:

| Label | Ruta |
|-------|------|
| General | `/general` |
| Users | `/auth` |
| Notifications | `/general/notifications` |
| API key | `/general/listing/api_key` |
| API Logs | `/api_stats` |
| Billing | `/general/listing/billing` |

Edit: `/general/edit`. API key: Generate `/general/add_api_key`, Edit/Delete `/general/edit_api_key/{id}`.

### API method limits (UI)

- Default: 1000/h, 10/s; burst &gt;15/s blocked.
- Heavy OLT detail: 30 calls / 10 min per OLT: `get_olt_cards_details`, `get_olt_pon_ports_details`, `get_olt_uplink_ports_details`.

### Users `/auth`

Columnas: Name | Email | 2F Auth | Group | Restriction group | Status | Last login | Action.

Acciones: Create user/group, View groups, Create/View restriction groups, View logs (`/auth/user_changes_logs`), enable OTP, edit user.

Usuario visto: `dieler.tk.s@gmail.com`, group `admins`, 2F Disabled, Active.

Billing: `GET system/get_billing_details` (API) — OK live previo.

## System config `/system_config`

Tabs: VPN tunnels | TR069 Profiles | TR069 status.

Tunnel types: OpenVPN TCP | OpenVPN UDP | WireGuard.

Notas UI: subnet tunnel tipica `10.69.69.0/24`; routes = subnets mgmt OLT/ONU; Mikrotik preferred; TR069 requiere tunnel + profile por OLT + attach a ONU.

## IspManager

P1: users + groups + API keys + billing read + rate limits. P2: TR069 ACS + VPN.
