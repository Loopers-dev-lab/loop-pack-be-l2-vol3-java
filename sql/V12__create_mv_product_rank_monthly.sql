CREATE TABLE mv_product_rank_monthly (
    year_month_key        VARCHAR(7)   NOT NULL,
    product_id        BIGINT       NOT NULL,
    ranking_position  INT          NOT NULL,
    score             DOUBLE       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (year_month_key, product_id),
    KEY idx_mv_product_rank_monthly_position (year_month_key, ranking_position)
);
