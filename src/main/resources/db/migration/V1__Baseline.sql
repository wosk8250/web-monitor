-- web-monitor 초기 스키마 (PostgreSQL)
-- 테이블 생성 순서: FK 참조 관계 기준

CREATE TABLE settings (
    id              BIGSERIAL PRIMARY KEY,
    discord_webhook_url VARCHAR(500),
    enabled         BOOLEAN,
    created_at      TIMESTAMP,
    updated_at      TIMESTAMP
);

CREATE TABLE sites (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    url                     VARCHAR(500) NOT NULL,
    active                  BOOLEAN,
    detect_content_change   BOOLEAN,
    last_content_hash       VARCHAR(64),
    article_selector        VARCHAR(500),
    check_interval_minutes  INTEGER,
    last_checked_at         TIMESTAMP,
    discord_user_id         VARCHAR(20),
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP
);

CREATE TABLE keywords (
    id          BIGSERIAL PRIMARY KEY,
    keyword     VARCHAR(100) NOT NULL,
    site_id     BIGINT REFERENCES sites(id),
    active      BOOLEAN,
    created_at  TIMESTAMP
);

CREATE TABLE products (
    id                      BIGSERIAL PRIMARY KEY,
    name                    VARCHAR(500),
    url                     VARCHAR(1000),
    shop_name               VARCHAR(100),
    current_status          VARCHAR(50),
    previous_status         VARCHAR(50),
    current_price           NUMERIC(10, 2),
    previous_price          NUMERIC(10, 2),
    image_url               VARCHAR(1000),
    content_selector        VARCHAR(500),
    last_content_hash       VARCHAR(64),
    active                  BOOLEAN,
    notify_on_restock       BOOLEAN,
    priority                VARCHAR(50),
    check_interval_minutes  INTEGER,
    last_checked_at         TIMESTAMP,
    last_restock_alert_at   TIMESTAMP,
    consecutive_failures    INTEGER,
    last_failure_at         TIMESTAMP,
    discord_user_id         VARCHAR(20),
    created_at              TIMESTAMP,
    updated_at              TIMESTAMP
);

CREATE TABLE alerts (
    id                  BIGSERIAL PRIMARY KEY,
    version             BIGINT,
    site_id             BIGINT REFERENCES sites(id),
    keyword_id          BIGINT REFERENCES keywords(id),
    product_id          BIGINT REFERENCES products(id),
    alert_type          VARCHAR(50),
    message             TEXT,
    page_title          VARCHAR(500),
    detected_url        VARCHAR(500),
    detected_at         TIMESTAMP,
    sent                BOOLEAN,
    sent_at             TIMESTAMP,
    priority            VARCHAR(50),
    retry_count         INTEGER,
    last_error_message  VARCHAR(500)
);

CREATE TABLE articles (
    id                  BIGSERIAL PRIMARY KEY,
    site_id             BIGINT NOT NULL REFERENCES sites(id),
    article_url         VARCHAR(1000),
    article_title       VARCHAR(500),
    article_id          VARCHAR(200),
    first_detected_at   TIMESTAMP
);

-- sites 인덱스
CREATE INDEX idx_site_active ON sites(active);
CREATE INDEX idx_site_discord_user ON sites(discord_user_id);

-- keywords 인덱스
CREATE INDEX idx_keyword_site_active ON keywords(site_id, active);
CREATE INDEX idx_keyword_active ON keywords(active);

-- alerts 인덱스
CREATE INDEX idx_alert_site ON alerts(site_id);
CREATE INDEX idx_alert_keyword ON alerts(keyword_id);
CREATE INDEX idx_alert_product ON alerts(product_id);
CREATE INDEX idx_alert_detected_at ON alerts(detected_at);
CREATE INDEX idx_alert_site_detected ON alerts(site_id, detected_at);
CREATE INDEX idx_alert_sent_priority ON alerts(sent, priority, detected_at);

-- products 인덱스
CREATE INDEX idx_product_active ON products(active);
CREATE INDEX idx_product_priority ON products(priority);
CREATE INDEX idx_product_active_priority ON products(active, priority);
CREATE INDEX idx_product_discord_user ON products(discord_user_id);

-- articles 인덱스 (article_url 유니크)
CREATE INDEX idx_article_site_article_id ON articles(site_id, article_id);
CREATE UNIQUE INDEX idx_article_site_article_url ON articles(site_id, article_url);
