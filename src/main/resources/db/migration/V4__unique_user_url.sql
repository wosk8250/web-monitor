-- TOCTOU 방어: 사용자별 URL 유니크 제약
-- existsBy 체크와 save 사이 동시 요청으로 인한 중복 행 생성 방지

CREATE UNIQUE INDEX IF NOT EXISTS idx_site_user_url ON sites(discord_user_id, url);
CREATE UNIQUE INDEX IF NOT EXISTS idx_product_user_url ON products(discord_user_id, url);
