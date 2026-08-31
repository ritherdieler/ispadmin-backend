-- Rekey ACS Wi-Fi samples by parameter timestamp, not lastInform.
-- Flyway is not enabled; apply explicitly. Safe to apply twice after the unique exists.

DELETE s FROM acs_wifi_station_sample s
INNER JOIN acs_wifi_count_sample c ON c.id = s.count_sample_id
WHERE c.observed_at IS NULL OR c.quality_status = 'MISSING';

DELETE FROM acs_wifi_count_sample
WHERE observed_at IS NULL OR quality_status = 'MISSING';

DELETE c FROM acs_wifi_count_sample c
INNER JOIN acs_wifi_count_sample keep
  ON keep.device_id = c.device_id
 AND keep.subscription_id = c.subscription_id
 AND keep.observed_at = c.observed_at
 AND keep.id > c.id;

ALTER TABLE acs_wifi_count_sample DROP INDEX uk_sh_wifi_reading;

ALTER TABLE acs_wifi_count_sample
  ADD CONSTRAINT uk_sh_wifi_reading UNIQUE (device_id, subscription_id, observed_at);

UPDATE acs_wifi_status_current c
INNER JOIN (
  SELECT subscription_id, MAX(id) AS id
  FROM acs_wifi_count_sample
  WHERE quality_status = 'FRESH'
  GROUP BY subscription_id
) latest ON latest.subscription_id = c.subscription_id
INNER JOIN acs_wifi_count_sample s ON s.id = latest.id
SET c.count_sample_id = s.id,
    c.inform_at = s.inform_at,
    c.observed_at = s.observed_at,
    c.associated_device_count = s.associated_device_count,
    c.quality_status = 'FRESH';
