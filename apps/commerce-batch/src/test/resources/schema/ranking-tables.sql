CREATE TABLE IF NOT EXISTS product_metrics
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    version     BIGINT,
    product_id  BIGINT NOT NULL,
    date        DATE   NOT NULL,
    like_count  INT    NOT NULL DEFAULT 0,
    order_count INT    NOT NULL DEFAULT 0,
    UNIQUE KEY uk_product_date (product_id, date)
);

CREATE TABLE IF NOT EXISTS mv_product_rank_weekly
(
    product_id      BIGINT PRIMARY KEY,
    like_count      INT          NOT NULL,
    order_count     INT          NOT NULL,
    score           DOUBLE       NOT NULL,
    year_month_week VARCHAR(10)  NOT NULL,
    updated_at      DATETIME     NOT NULL
);

CREATE TABLE IF NOT EXISTS mv_product_rank_monthly
(
    product_id     BIGINT PRIMARY KEY,
    like_count     INT         NOT NULL,
    order_count    INT         NOT NULL,
    score          DOUBLE      NOT NULL,
    ranking_period VARCHAR(10) NOT NULL,
    updated_at     DATETIME    NOT NULL
);
