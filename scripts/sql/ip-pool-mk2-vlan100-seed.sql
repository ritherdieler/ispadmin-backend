-- IP pool piloto MK2 / VLAN 100 (Mikrotik CCR 2, network_device.id=8)
-- Segmento alineado con gateway RouterOS: 192.168.30.1/24 en sfp-sfpplus2
-- Convención ip_segment: gateway .1/24 (igual que pools elegibles de MK1)
-- Ver .agent-docs/infra-red-multi-mikrotik-gigafiber.md

INSERT INTO ip_pool (id, created_at, ip_segment, host_device_id, is_eligible)
SELECT 8, UNIX_TIMESTAMP() * 1000, '192.168.30.1/24', 8, b'1'
WHERE NOT EXISTS (
    SELECT 1 FROM ip_pool WHERE id = 8 OR ip_segment = '192.168.30.1/24'
);

UPDATE ip_pool
SET is_eligible = b'0'
WHERE host_device_id = 1
  AND is_eligible = b'1';

UPDATE ip_pool
SET is_eligible = b'1',
    host_device_id = 8
WHERE ip_segment = '192.168.30.1/24';

SELECT id, ip_segment, host_device_id, is_eligible, created_at
FROM ip_pool
WHERE host_device_id = 8 OR ip_segment = '192.168.30.1/24'
ORDER BY id;

SELECT id, ip_segment, host_device_id, is_eligible
FROM ip_pool
WHERE is_eligible = b'1'
ORDER BY id;
