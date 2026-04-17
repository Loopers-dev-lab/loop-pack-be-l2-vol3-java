-- Round 10: 주간/월간 랭킹 MV (운영 스키마는 Flyway로만 변경; JPA 엔티티와 동일 스키마 유지)
CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_key VARCHAR(16) NOT NULL,
    product_id BIGINT NOT NULL,
    `rank` INT NOT NULL,
    score DECIMAL(24, 8) NOT NULL,
    version INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mv_product_rank_weekly_period_product (period_key, product_id),
    KEY idx_mv_product_rank_weekly_period_rank (period_key, `rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
    id BIGINT NOT NULL AUTO_INCREMENT,
    period_key VARCHAR(16) NOT NULL,
    product_id BIGINT NOT NULL,
    `rank` INT NOT NULL,
    score DECIMAL(24, 8) NOT NULL,
    version INT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_mv_product_rank_monthly_period_product (period_key, product_id),
    KEY idx_mv_product_rank_monthly_period_rank (period_key, `rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
