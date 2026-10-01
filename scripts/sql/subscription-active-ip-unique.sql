-- One-shot, manual: run only after the duplicated active IPs were resolved with the business.

DROP PROCEDURE IF EXISTS gf_assert_no_active_ip_duplicates;
DELIMITER //
CREATE PROCEDURE gf_assert_no_active_ip_duplicates()
BEGIN
    IF EXISTS (
        SELECT ip FROM subscription
        WHERE ip IS NOT NULL AND ip <> '' AND service_status <> 'CANCELLED'
        GROUP BY ip HAVING COUNT(*) > 1
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ACTIVE_IP_DUPLICATES_PRESENT';
    END IF;
END //
DELIMITER ;
CALL gf_assert_no_active_ip_duplicates();
DROP PROCEDURE gf_assert_no_active_ip_duplicates;

ALTER TABLE subscription
    ADD COLUMN active_ip VARCHAR(255)
        GENERATED ALWAYS AS (IF(service_status <> 'CANCELLED' AND ip <> '', ip, NULL)) STORED,
    ADD UNIQUE INDEX uk_subscription_active_ip (active_ip);
