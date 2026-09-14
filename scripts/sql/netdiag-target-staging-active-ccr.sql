-- NetDiag target único para pruebas del 360 en staging.
-- Ejecutar conectado a la base ispadmin_staging.
-- Selecciona el CCR2/MK2 si está activo; si no, elige otro CLOUD_CORE_ROUTER activo.

SET @active_ccr_id := (
    SELECT id
    FROM network_device
    WHERE network_device_type = 'CLOUD_CORE_ROUTER'
      AND disabled = 0
    ORDER BY (ip_address = '38.224.231.4') DESC, id DESC
    LIMIT 1
);

SET @active_ccr_config := '{"kind":"mikrotik","criticalInterfaces":["ether1","ether3","sfp-sfpplus1","sfp-sfpplus2","sfp-sfpplus3","LAN","LAN_MK1","WAN-VLAN SFP-SFPPLUS1","wg-ispadmin-vps","lo"],"expectedFirmware":"7.23.2","netwatchNames":["upstream-http","upstream-dns"],"opticalInterfaces":["sfp-sfpplus1","sfp-sfpplus2","sfp-sfpplus3"]}';

INSERT INTO net_diag_target
    (name, device_ref_id, enabled, poll_interval_ms, monitor_config, created_at, updated_at)
SELECT
    'STAGING-CCR-ACTIVE', @active_ccr_id, true, 60000, @active_ccr_config, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3)
FROM DUAL
WHERE @active_ccr_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM net_diag_target WHERE name = 'STAGING-CCR-ACTIVE');

UPDATE net_diag_target
SET device_ref_id = @active_ccr_id,
    enabled = true,
    poll_interval_ms = 60000,
    monitor_config = @active_ccr_config,
    updated_at = UTC_TIMESTAMP(3)
WHERE name = 'STAGING-CCR-ACTIVE'
  AND @active_ccr_id IS NOT NULL;

SELECT id, name, device_ref_id, enabled, poll_interval_ms, monitor_config
FROM net_diag_target
WHERE name = 'STAGING-CCR-ACTIVE';
