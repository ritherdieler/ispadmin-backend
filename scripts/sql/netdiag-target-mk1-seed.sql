-- NetDiag seed MK1 — todas las interfaces reportadas por /interface (2026-07-27, 38.224.231.2)
-- Requiere fila en network_device con ip_address = '38.224.231.2' (prod: id=1)

SET @mk1_device_id := (
    SELECT id FROM network_device WHERE ip_address = '38.224.231.2' ORDER BY id LIMIT 1
);

SET @monitor_config := JSON_OBJECT(
    'criticalInterfaces', JSON_ARRAY(
        'ether1',
        'ether2',
        'ether3',
        'ether4',
        'ether5',
        'ether6',
        'ether7',
        'ether8',
        'sfp-sfpplus1',
        'sfp-sfpplus2',
        'LAN',
        'SERVICIO CORP.TARAZONA',
        'eoip-tunnel1',
        'gre-ispadmin-vps',
        'lo',
        'vlan1'
    ),
    'expectedFirmware', '7.23.2',
    'netwatchNames', JSON_ARRAY('upstream-http', 'upstream-dns'),
    'opticalInterfaces', JSON_ARRAY('sfp-sfpplus1', 'sfp-sfpplus2')
);

INSERT INTO net_diag_target (name, device_ref_id, enabled, poll_interval_ms, monitor_config, created_at, updated_at)
SELECT
    'MK1',
    @mk1_device_id,
    true,
    60000,
    @monitor_config,
    NOW(),
    NOW()
WHERE @mk1_device_id IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM net_diag_target WHERE name = 'MK1');

UPDATE net_diag_target
SET device_ref_id = COALESCE(@mk1_device_id, device_ref_id),
    enabled = true,
    poll_interval_ms = 60000,
    monitor_config = @monitor_config,
    updated_at = NOW()
WHERE name = 'MK1';

SELECT id, name, device_ref_id, enabled, monitor_config
FROM net_diag_target
WHERE name = 'MK1';
