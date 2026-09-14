UPDATE subscription
SET provision_attempt_count = 0
WHERE provision_attempt_count IS NULL;

ALTER TABLE subscription
    MODIFY COLUMN provision_attempt_count INT NOT NULL DEFAULT 0;
