-- Discard V2 child records left behind by hard-cleanup before enforcing referential integrity.
DELETE FROM provisioning_v2_operator_lock
WHERE NOT EXISTS (
    SELECT 1 FROM provisioning_v2_operation
    WHERE provisioning_v2_operation.operation_id = provisioning_v2_operator_lock.operation_id
);

DELETE FROM provisioning_v2_event
WHERE NOT EXISTS (
    SELECT 1 FROM provisioning_v2_operation
    WHERE provisioning_v2_operation.operation_id = provisioning_v2_event.operation_id
);

DELETE FROM provisioning_v2_resource
WHERE NOT EXISTS (
    SELECT 1 FROM provisioning_v2_operation
    WHERE provisioning_v2_operation.operation_id = provisioning_v2_resource.operation_id
);

ALTER TABLE provisioning_v2_operator_lock
    ADD CONSTRAINT fk_provisioning_v2_operator_lock_operation
        FOREIGN KEY (operation_id) REFERENCES provisioning_v2_operation (operation_id) ON DELETE CASCADE;

ALTER TABLE provisioning_v2_event
    ADD CONSTRAINT fk_provisioning_v2_event_operation
        FOREIGN KEY (operation_id) REFERENCES provisioning_v2_operation (operation_id) ON DELETE CASCADE;

ALTER TABLE provisioning_v2_resource
    ADD CONSTRAINT fk_provisioning_v2_resource_operation
        FOREIGN KEY (operation_id) REFERENCES provisioning_v2_operation (operation_id) ON DELETE CASCADE;
