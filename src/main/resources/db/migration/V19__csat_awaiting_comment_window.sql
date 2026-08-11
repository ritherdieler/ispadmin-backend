ALTER TABLE csat_survey
    ADD COLUMN comment_window_expires_at DATETIME NULL AFTER responded_at;

CREATE INDEX idx_csat_survey_comment_window
    ON csat_survey (status, comment_window_expires_at);
