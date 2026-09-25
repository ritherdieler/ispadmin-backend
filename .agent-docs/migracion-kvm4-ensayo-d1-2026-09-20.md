# Ensayo KVM4 D-1 (2026-09-20)

Ensayo contra `2.24.66.53` **sin** cambiar DNS `.cloud` ni `/etc/hosts`. SNI: `curl --resolve api.gigafiberperu.cloud:443:2.24.66.53`.

## Resultado

| Check | OK |
|---|---|
| Flyway prod V38–V46, V48–V55 `success=1` | sí |
| API HTTPS `/ispadmin/` 200 | sí |
| Staging `/ispadmin-staging/` 200 | sí |
| Login ADMIN `dscorp` prod+staging | sí |
| 360 GET staging `#35` (ONU lab) | sí |
| 360 GET prod `#2376` | sí |
| ACS `:80` sin 301 (405 POST only) | sí |
| NBI local `:7557` + device lab | sí (Inform freeze: DNS `acs.` sigue en prod) |
| Restore MySQL | 724 s ≈ 12 min |
| Writes/scheduling off; sin SSH OLT | sí |

`.tech` HTTPS no valida (cert `.cloud`). No bloquea el cutover `.cloud`.

Ventana: sigue bloqueada hasta OK explícito.
