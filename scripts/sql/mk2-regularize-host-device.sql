-- MK2 regularization: point measurable subscriptions to host_device_id = 8
-- Run against prod via tunnel: ./scripts/db-tunnel.sh start
-- mysql -h 127.0.0.1 -P 13306 -u root -p ispadmin < scripts/sql/mk2-regularize-host-device.sql

-- 1) Dry-run: rows that would change
SELECT id, ip, host_device_id, service_status, installation_type
FROM subscription
WHERE service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
  AND ip IS NOT NULL AND ip <> ''
  AND (installation_type IS NULL OR installation_type <> 'ONLY_TV_FIBER')
  AND (host_device_id IS NULL OR host_device_id <> 8);

-- 2) Count only
-- SELECT COUNT(*) AS to_update FROM subscription
-- WHERE service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
--   AND ip IS NOT NULL AND ip <> ''
--   AND (installation_type IS NULL OR installation_type <> 'ONLY_TV_FIBER')
--   AND (host_device_id IS NULL OR host_device_id <> 8);

-- 3) Apply (uncomment after reviewing dry-run)
-- UPDATE subscription
-- SET host_device_id = 8
-- WHERE service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
--   AND ip IS NOT NULL AND ip <> ''
--   AND (installation_type IS NULL OR installation_type <> 'ONLY_TV_FIBER')
--   AND (host_device_id IS NULL OR host_device_id <> 8);
