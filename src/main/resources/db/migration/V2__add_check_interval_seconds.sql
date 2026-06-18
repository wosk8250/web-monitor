-- URGENT 제품의 초 단위 체크 주기 지원 (null이면 checkIntervalMinutes 사용)
-- IF NOT EXISTS: V1에서 컬럼이 이미 포함된 신규 PostgreSQL DB에서도 안전하게 실행
ALTER TABLE products ADD COLUMN IF NOT EXISTS check_interval_seconds INTEGER
    CHECK (check_interval_seconds IS NULL OR (check_interval_seconds >= 20 AND check_interval_seconds <= 3600));
