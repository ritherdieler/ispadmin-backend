# VLAN 1 en desuso — destino VLAN 100

Decisión **2026-09-10**. La VLAN **1** no se agranda: queda como parque legado y **irá quedando en desuso**. La intención es **migrar todo a la VLAN 100** (internet de abonado). No es un big-bang; son oleadas.

La VLAN **1000** no sustituye a la 1: es solo gestión de CPE (TR-069). El abonado de internet vive en la **100**.

## Destino

```text
 hoy                         destino
 ─────────────────────       ─────────────────────────────
 VLAN 1  (legado, 0/3/3)  →  se vacía
 VLAN 100 (0/3/2)         →  todo el internet de abonado
 VLAN 1000 (0/3/2)        →  gestión CPE (independiente)
```

Cuando el residual de VLAN 1 sea cero, `0/3/3` / `sfp-sfpplus3` / el bridge `LAN-VLAN1` dejan de ser el camino de clientes GPON. Qué se hace con ese puerto (standby, LACP, otro uso) se decide entonces: [olt-mk2-uplinks-decision-2026-09-10.md](./olt-mk2-uplinks-decision-2026-09-10.md).

`ether5` (switch SFP) y `ether7` (AirFiber 9 Octubre) y el PPPoE de `192.168.26.0/24` viven hoy en el mismo dominio L2 que la VLAN 1. **Migrar “todo a la 100” incluye esos enlaces**, no solo las ONUs. Hasta que se muevan, el bridge `LAN-VLAN1` tiene que existir.

## Qué no es esta decisión

- No apagar `0/3/3` ni el bridge mientras haya residual.
- No unificar puertos ahora (siguen dos NNI hasta que el parque VLAN 1 baje).
- No pintar tagged 1000 en `0/3/3`: las altas nuevas y el lab cuelgan de `0/3/2`.
- No borrar pools ni gateways de VLAN 1 en el mismo turno que una oleada a medias.

## Cómo se migra (cuando se ejecute)

1. Reconciliar inventario: OLT service-port **100** y SmartOLT/`subscription.vlan` tienen que decir lo mismo. Gate de la auditoría: **235 ONUs** con SP 100 y `vlan=1` en CloudOLT — [auditoria-red-olt-mk2-2026-09-09.md](./auditoria-red-olt-mk2-2026-09-09.md).
2. Oleada: WAN del CPE a VLAN 100 (o SP dual durante la ventana) + gateway L3 en `vlan100-olt`.
3. Altas nuevas: VLAN **100** (internet) + **1000** (gestión, cuando el authorize lo haga). No abrir VLAN 1 a parque nuevo.
4. Al final: `ether5` / `ether7` / PPPoE al dominio de la 100; entonces sí reevaluar un bridge con `vlan-filtering`.

El backend aún **acepta** `subscription.vlan=1` (`SubscriptionVlanRules.ALLOWED`) porque el residual existe. El valor por defecto de migración vacía ya es **100**.

## Relacionado

- [mk2-nombres-canonicos.md](./mk2-nombres-canonicos.md)
- [cutover-vlan100-mk2-tagged-2026-09-10.md](./cutover-vlan100-mk2-tagged-2026-09-10.md)
- [vlan1000-gestion-cpe.md](./vlan1000-gestion-cpe.md)
- [infra-red-multi-mikrotik-gigafiber.md](./infra-red-multi-mikrotik-gigafiber.md)
