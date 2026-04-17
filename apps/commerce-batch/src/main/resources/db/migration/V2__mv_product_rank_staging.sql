-- Round 10: 집계 결과를 MV 반영 전에 담는 스테이징 테이블
CREATE TABLE IF NOT EXISTS mv_product_rank_staging (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_type VARCHAR(16) NOT NULL,
    period_key VARCHAR(16) NOT NULL,
    product_id BIGINT NOT NULL,
    `rank` INT NOT NULL,
    score DECIMAL(24, 8) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mv_rank_staging_period_product (period_type, period_key, product_id),
    KEY idx_mv_rank_staging_period_rank (period_type, period_key, `rank`)
);
