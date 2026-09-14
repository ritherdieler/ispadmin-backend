UPDATE assistance_ticket
SET priority = CASE
    WHEN priority IS NULL OR TRIM(priority) = '' THEN '0'
    WHEN priority REGEXP '^[0-9]+$' THEN priority
    ELSE '0'
END;

ALTER TABLE assistance_ticket
    MODIFY COLUMN priority INT NOT NULL DEFAULT 0;
