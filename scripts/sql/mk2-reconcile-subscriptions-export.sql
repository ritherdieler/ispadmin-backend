SELECT
  s.id,
  s.ip,
  s.first_name,
  s.last_name,
  s.installation_type,
  s.service_status,
  s.host_device_id,
  p.name AS plan_name,
  p.type AS plan_type,
  p.upload_speed,
  p.download_speed,
  pl.name AS place_name,
  n.code AS nap_code
FROM subscription s
LEFT JOIN plan p ON p.id = s.plan_id
LEFT JOIN place pl ON pl.id = s.place_id
LEFT JOIN nap_box n ON n.id = s.napbox_id
WHERE s.host_device_id = 8
  AND s.service_status IN ('ACTIVE', 'CUT_OFF', 'SUSPENDED')
  AND s.ip IS NOT NULL AND s.ip <> ''
  AND (s.installation_type IS NULL OR s.installation_type <> 'ONLY_TV_FIBER')
ORDER BY s.id;
