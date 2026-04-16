CREATE TABLE mv_product_rank_weekly (
    year_week         VARCHAR(10)  NOT NULL,
    product_id        BIGINT       NOT NULL,
    ranking_position  INT          NOT NULL,
    score             DOUBLE       NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (year_week, product_id),
    KEY idx_mv_product_rank_weekly_position (year_week, ranking_position)
);
