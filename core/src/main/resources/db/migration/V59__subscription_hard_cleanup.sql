CREATE TABLE IF NOT EXISTS subscription_hard_cleanup (
  subscription_id INT NOT NULL PRIMARY KEY,
  snapshot_json TEXT NOT NULL,
  steps_json TEXT NOT NULL,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
