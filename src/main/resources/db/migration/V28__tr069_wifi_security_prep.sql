ALTER TABLE tr069_model_profile
    ADD COLUMN wifi_security_prep_json TEXT NOT NULL DEFAULT '[]' AFTER wlan5_path;
