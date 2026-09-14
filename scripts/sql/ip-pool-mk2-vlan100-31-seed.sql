-- IP pool adicional MK2 / VLAN 100 (Mikrotik CCR 2, network_device.id=8)
-- Segmento: 192.168.31.1/24 en vlan100-olt
-- Convención ip_segment: gateway .1/24
-- 2026-09-10: la elegibilidad en prod la cambia
--   scripts/sql/ip-pool-mk2-vlan100-31-eligible-switch.sql
--   (.31 elegible, .30 no). No reejecutar este seed en prod.

INSERT INTO ip_pool (created_at, ip_segment, host_device_id, is_eligible)
SELECT UNIX_TIMESTAMP() * 1000, '192.168.31.1/24', 8, b'1'
WHERE NOT EXISTS (
    SELECT 1 FROM ip_pool WHERE ip_segment = '192.168.31.1/24'
);

UPDATE ip_pool
SET is_eligible = b'1',
    host_device_id = 8
WHERE ip_segment = '192.168.31.1/24';

SELECT id, ip_segment, host_device_id, is_eligible, created_at
FROM ip_pool
WHERE ip_segment IN ('192.168.30.1/24', '192.168.31.1/24')
ORDER BY id;

SELECT id, ip_segment, host_device_id, is_eligible
FROM ip_pool
WHERE is_eligible = b'1'
ORDER BY id;
