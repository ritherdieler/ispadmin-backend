-- Switch prod: altas MK2 VLAN100 usan 192.168.31.1/24; 192.168.30.1/24 deja de ser elegible.
-- No borra el gateway .30 en MK2 (166 abonados activos siguen en .30).
-- Requiere WAR Core con SubscriptionVlanRules.PROD_VLAN100_POOL_PREFIX_31 (post 69f6674).
-- Ver .agent-docs/mk2-pool-31-eligible-switch-2026-09-10.md

INSERT INTO ip_pool (id, created_at, ip_segment, host_device_id, is_eligible)
SELECT 163, UNIX_TIMESTAMP() * 1000, '192.168.31.1/24', 8, b'1'
WHERE NOT EXISTS (
    SELECT 1 FROM ip_pool WHERE ip_segment = '192.168.31.1/24'
);

UPDATE ip_pool
SET is_eligible = b'0'
WHERE ip_segment = '192.168.30.1/24';

UPDATE ip_pool
SET is_eligible = b'1',
    host_device_id = 8
WHERE ip_segment = '192.168.31.1/24';

SELECT id, ip_segment, host_device_id, CAST(is_eligible AS UNSIGNED) AS elig
FROM ip_pool
WHERE ip_segment IN ('192.168.30.1/24', '192.168.31.1/24')
   OR is_eligible = b'1'
ORDER BY id;
