-- NetDiag MK2 (38.224.231.4) — incluye túnel VPS wg-ispadmin-vps (prod 2026-08-01)
-- Idempotente: crea MK2 si no existe; actualiza monitor_config y device_ref_id si ya existe.

SET @mk2_device_id := (SELECT id FROM network_device WHERE ip_address = '38.224.231.4' LIMIT 1);
SET @mk2_config := '{"criticalInterfaces":["ether1","ether3","sfp-sfpplus1","sfp-sfpplus2","sfp-sfpplus3","LAN","LAN_MK1","WAN-VLAN SFP-SFPPLUS1","wg-ispadmin-vps","lo"],"expectedFirmware":"7.23.2","netwatchNames":["upstream-http","upstream-dns"],"opticalInterfaces":["sfp-sfpplus1","sfp-sfpplus2","sfp-sfpplus3"]}';

INSERT INTO net_diag_target (name, device_ref_id, enabled, poll_interval_ms, monitor_config, created_at, updated_at)
SELECT 'MK2', @mk2_device_id, true, 60000, @mk2_config, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM DUAL
WHERE @mk2_device_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM net_diag_target WHERE name = 'MK2');

UPDATE net_diag_target
SET device_ref_id = @mk2_device_id,
    monitor_config = @mk2_config,
    updated_at = UTC_TIMESTAMP(3)
WHERE name = 'MK2'
  AND @mk2_device_id IS NOT NULL;

-- MK1 ya no aloja el túnel VPS→OLT; evita falsos GRE_TUNNEL_DOWN si gre-ispadmin-vps desaparece en MK1.
UPDATE net_diag_target
SET monitor_config = REPLACE(monitor_config, '"gre-ispadmin-vps", ', ''),
    updated_at = UTC_TIMESTAMP(3)
WHERE name = 'MK1'
  AND monitor_config LIKE '%gre-ispadmin-vps%';
