-- Seed vlan_id en CLOUD_CORE_ROUTER (multi-MikroTik GigaFiber)
-- Ejecutar en MySQL prod tras deploy con columna vlan_id (JPA ddl-auto=update)
-- Ver .agent-docs/infra-red-multi-mikrotik-gigafiber.md

UPDATE network_device SET vlan_id = 1 WHERE id = 1;
UPDATE network_device SET vlan_id = 100 WHERE id = 8;

SELECT id, name, ip_address, network_device_type, vlan_id
FROM network_device
WHERE id IN (1, 8);
