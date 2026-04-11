CREATE TABLE ranking_score_ledger (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    bucket_type     VARCHAR(8)   NOT NULL,
    bucket_key      VARCHAR(16)  NOT NULL,
    product_id      BIGINT       NOT NULL,
    base_points     DOUBLE       NOT NULL,
    last_scored_at  DATETIME(6)  NOT NULL,
    dirty           TINYINT(1)   NOT NULL,
    version         BIGINT       NULL,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    deleted_at      DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ranking_score_ledger_bucket_product
        UNIQUE (bucket_type, bucket_key, product_id),
    KEY idx_ranking_score_ledger_dirty (bucket_type, bucket_key, dirty)
);
