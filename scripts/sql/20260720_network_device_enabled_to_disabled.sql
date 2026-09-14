ALTER TABLE network_device ADD COLUMN disabled TINYINT(1) NOT NULL DEFAULT 0;

UPDATE network_device SET disabled = NOT enabled WHERE enabled IS NOT NULL;
