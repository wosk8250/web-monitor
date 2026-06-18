-- TOCTOU 방어: 다중 인스턴스 동시 기동 시 중복 Setting INSERT 차단
-- DataIntegrityViolationException → SettingInitializer가 INFO 로그 후 정상 종료
CREATE UNIQUE INDEX IF NOT EXISTS idx_setting_discord_webhook_url
    ON settings (discord_webhook_url);
