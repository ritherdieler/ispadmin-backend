# Traffic fase 3 — allowlist RouterOS (2026-09-16)

`RouterOsCommandUseCase` rechaza paths que Core no usa. Fallo `Result` sin abrir MikroTik. El WS de suscripción no se revive. `traffic.poll.enabled` sigue en `true`.

Paths permitidos: `/queue/simple`, `/ppp/secret`, `/ppp/active`, `/ppp/profile`, `/interface`, `/interface/monitor-traffic`, `/interface/ethernet/monitor`, `/system/resource`, `/system/identity`, `/system/package`, `/system/health`, `/system/routerboard`, `/ip/firewall/address-list`, `/ip/firewall/filter`, `/ip/address`, `/tool/netwatch`.

Verificado: `:traffic:test` 114 OK; `:traffic:compileKotlin` y `:core:compileKotlin` OK. `RouterOsPathAllowlistTest` fija exactamente ese set.
