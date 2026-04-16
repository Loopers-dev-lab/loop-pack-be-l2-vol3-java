CREATE TABLE mv_product_rank_monthly_staging (
    year_month_key  VARCHAR(7)   NOT NULL,
    product_id  BIGINT       NOT NULL,
    score       DOUBLE       NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (year_month_key, product_id)
);
