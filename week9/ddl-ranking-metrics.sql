-- 랭킹 집계용 bucket 메트릭 테이블 (5분 단위)

CREATE TABLE IF NOT EXISTS product_view_metrics (
    product_id   BIGINT NOT NULL,
    bucket_time  DATETIME NOT NULL,
    view_count   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, bucket_time),
    INDEX idx_pvm_bucket_time (bucket_time)
);

CREATE TABLE IF NOT EXISTS product_like_metrics (
    product_id   BIGINT NOT NULL,
    bucket_time  DATETIME NOT NULL,
    like_count   BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, bucket_time),
    INDEX idx_plm_bucket_time (bucket_time)
);

CREATE TABLE IF NOT EXISTS product_order_metrics (
    product_id   BIGINT NOT NULL,
    bucket_time  DATETIME NOT NULL,
    order_count  INT    NOT NULL DEFAULT 0,
    quantity     BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, bucket_time),
    INDEX idx_pom_bucket_time (bucket_time)
);
