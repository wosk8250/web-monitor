ALTER TABLE products ADD COLUMN IF NOT EXISTS last_content_change_alert_at TIMESTAMP;
ALTER TABLE products ADD COLUMN IF NOT EXISTS notify_on_content_change BOOLEAN NOT NULL DEFAULT TRUE;
