UPDATE network_device
SET disabled = 1
WHERE network_device_type = 'CLOUD_CORE_ROUTER'
  AND (
    id <> 8
    AND LOWER(TRIM(name)) <> LOWER('Mikrotik CCR 2')
  );

UPDATE network_device
SET disabled = 0
WHERE network_device_type = 'CLOUD_CORE_ROUTER'
  AND (
    id = 8
    OR LOWER(TRIM(name)) = LOWER('Mikrotik CCR 2')
  );

SELECT id, name, ip_address, network_device_type, vlan_id, disabled
FROM network_device
WHERE network_device_type = 'CLOUD_CORE_ROUTER'
ORDER BY id;
