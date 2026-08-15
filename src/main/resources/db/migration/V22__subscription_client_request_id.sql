ALTER TABLE subscription ADD COLUMN client_request_id VARCHAR(64) NULL;
CREATE UNIQUE INDEX uk_subscription_client_request_id ON subscription (client_request_id);
