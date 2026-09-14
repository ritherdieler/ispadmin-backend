-- Fix MySQL longtext columns that block Hibernate indexes (dev/prod).
-- Safe to re-run if columns are already VARCHAR(300).

ALTER TABLE obs_endpoint_metric MODIFY COLUMN route VARCHAR(300) NULL;
ALTER TABLE obs_rum_metric MODIFY COLUMN page VARCHAR(300) NULL;
ALTER TABLE obs_symbol_artifact MODIFY COLUMN bundle VARCHAR(300) NULL;

-- Create indexes if missing (ignore duplicate errors manually if needed)
CREATE INDEX idx_obs_metric_route ON obs_endpoint_metric (route);
CREATE INDEX idx_obs_metric_bucket_route ON obs_endpoint_metric (bucket_start, route);
CREATE INDEX idx_obs_rum_page ON obs_rum_metric (page);
CREATE INDEX idx_obs_rum_bucket_page_platform_metric ON obs_rum_metric (bucket_start, page, platform, metric_name);
CREATE INDEX idx_obs_symbol_bundle ON obs_symbol_artifact (bundle);
