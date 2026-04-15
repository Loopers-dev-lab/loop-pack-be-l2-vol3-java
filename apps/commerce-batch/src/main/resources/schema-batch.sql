-- MV: Phase 1 산출물 — 일별 상품 점수
CREATE TABLE IF NOT EXISTS mv_product_score_daily (
    product_db_id  BIGINT         NOT NULL,
    score_date     DATE           NOT NULL,
    score          DOUBLE         NOT NULL,
    view_count     BIGINT         NOT NULL DEFAULT 0,
    like_count     BIGINT         NOT NULL DEFAULT 0,
    order_amount   DECIMAL(15,2)  NOT NULL DEFAULT 0,
    created_at     DATETIME       NOT NULL,
    updated_at     DATETIME       NOT NULL,
    PRIMARY KEY (product_db_id, score_date),
    INDEX idx_score_date (score_date)
);

-- MV: Phase 2 산출물 — 주간 랭킹 TOP 100 (S2 version-based)
CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
    period_key     VARCHAR(8)     NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 1,
    rank_no        INT            NOT NULL,
    ref_product_id BIGINT         NOT NULL,
    score          DOUBLE         NOT NULL,
    view_count     BIGINT         NOT NULL DEFAULT 0,
    like_count     BIGINT         NOT NULL DEFAULT 0,
    order_amount   DECIMAL(15,2)  NOT NULL DEFAULT 0,
    created_at     DATETIME       NOT NULL,
    updated_at     DATETIME       NOT NULL,
    PRIMARY KEY (period_key, version, rank_no),
    UNIQUE KEY uk_period_version_product (period_key, version, ref_product_id)
);

-- MV: Phase 2 산출물 — 월간 랭킹 TOP 100 (S2 version-based)
CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
    period_key     VARCHAR(8)     NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 1,
    rank_no        INT            NOT NULL,
    ref_product_id BIGINT         NOT NULL,
    score          DOUBLE         NOT NULL,
    view_count     BIGINT         NOT NULL DEFAULT 0,
    like_count     BIGINT         NOT NULL DEFAULT 0,
    order_amount   DECIMAL(15,2)  NOT NULL DEFAULT 0,
    created_at     DATETIME       NOT NULL,
    updated_at     DATETIME       NOT NULL,
    PRIMARY KEY (period_key, version, rank_no),
    UNIQUE KEY uk_period_version_product_monthly (period_key, version, ref_product_id)
);

-- Publication pointer — periodKey별 current published_version 추적 (S2 원자 발행)
CREATE TABLE IF NOT EXISTS mv_product_rank_publication (
    period_type       VARCHAR(20)  NOT NULL,
    period_key        VARCHAR(8)   NOT NULL,
    published_version BIGINT       NOT NULL DEFAULT 0,
    next_version      BIGINT       NOT NULL DEFAULT 0,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (period_type, period_key)
);
