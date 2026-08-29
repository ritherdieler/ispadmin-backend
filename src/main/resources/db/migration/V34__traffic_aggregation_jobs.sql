CREATE TABLE traffic_aggregation_watermark (
    layer VARCHAR(32) NOT NULL PRIMARY KEY,
    consolidated_through DATETIME NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE traffic_aggregation_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    layer VARCHAR(32) NOT NULL,
    started_at DATETIME NOT NULL,
    completed_at DATETIME NULL,
    status VARCHAR(24) NOT NULL,
    window_from DATETIME NULL,
    window_to DATETIME NULL,
    rows_read INT NOT NULL DEFAULT 0,
    rows_written INT NOT NULL DEFAULT 0,
    pending_windows INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500) NULL,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    INDEX idx_traffic_agg_run_layer_started (layer, started_at)
);
