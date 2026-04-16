CREATE TABLE mv_product_rank_weekly_staging (
    year_week   VARCHAR(10)  NOT NULL,
    product_id  BIGINT       NOT NULL,
    score       DOUBLE       NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (year_week, product_id)
);
