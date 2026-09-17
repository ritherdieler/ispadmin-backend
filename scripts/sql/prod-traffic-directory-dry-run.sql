-- SELECT only. Dry-run the prod Traffic directory after V52 DDL (STATIC_IP).
-- Run with USE ispadmin. Do not run against the staging Core schema.
-- Staging lab subscriptions #35 / #36 and queues *89* live in staging, not here.
-- Do not mix this result with a staging poll on the shared MK2.

SELECT COUNT(*) AS subscriptions
FROM subscription;

SELECT COUNT(*) AS with_ip
FROM subscription
WHERE ip IS NOT NULL AND TRIM(ip) <> '';

SELECT host_device_id, COUNT(*) AS n
FROM subscription
WHERE ip IS NOT NULL AND TRIM(ip) <> ''
GROUP BY host_device_id
ORDER BY n DESC;

-- After V52 without backfill every row stays STATIC_IP (column default).
-- access_mode is absent on live prod today; this query is for the scratch clone
-- or for post-cutover verification.
-- SELECT access_mode, COUNT(*) FROM subscription GROUP BY access_mode;
