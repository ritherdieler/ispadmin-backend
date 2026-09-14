-- Pool dedicado de staging en MK2. No solapa con ispadmin.ip_pool.
-- Segmento reservado: 192.168.250.1/24 (compatible VLAN 100 con gigafiber.environment.tag=stg)
-- Aplicado en ispadmin_staging (host_device_id = 8, Mikrotik CCR 2).

INSERT INTO ispadmin_staging.network_device
SELECT *
FROM ispadmin.network_device
WHERE id = 8
  AND NOT EXISTS (
    SELECT 1 FROM ispadmin_staging.network_device d WHERE d.id = 8
  );

DELETE FROM ispadmin_staging.ip_pool
WHERE ip_segment <> '192.168.250.1/24';

INSERT INTO ispadmin_staging.ip_pool (id, created_at, ip_segment, host_device_id, is_eligible)
SELECT 250, UNIX_TIMESTAMP() * 1000, '192.168.250.1/24', 8, 1
FROM DUAL
WHERE NOT EXISTS (
  SELECT 1 FROM ispadmin_staging.ip_pool p WHERE p.ip_segment = '192.168.250.1/24'
);

SELECT p.ip_segment
FROM ispadmin.ip_pool p
WHERE p.ip_segment = '192.168.250.1/24';
