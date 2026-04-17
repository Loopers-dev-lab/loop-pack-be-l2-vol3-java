-- Batch E2E 테스트용 도메인 테이블 DDL
-- commerce-batch는 도메인 Entity가 없으므로 Hibernate ddl-auto로 생성되지 않는다.
-- Tasklet이 참조하는 테이블만 최소한으로 정의한다.

CREATE TABLE IF NOT EXISTS brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    category_id BIGINT,
    name VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    stock_quantity INT NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    total_price INT NOT NULL,
    original_total_price INT NOT NULL,
    discount_amount INT NOT NULL DEFAULT 0,
    coupon_issue_id BIGINT,
    version BIGINT,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    product_price INT NOT NULL,
    brand_name VARCHAR(255),
    quantity INT NOT NULL
);

CREATE TABLE IF NOT EXISTS coupon (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    discount_type VARCHAR(50) NOT NULL,
    discount_value INT NOT NULL,
    min_order_amount INT NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    max_issuance_count INT,
    issued_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS coupon_issue (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    used_order_id BIGINT,
    status VARCHAR(50) NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL
);

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    amount INT NOT NULL,
    card_type VARCHAR(255),
    card_no VARCHAR(255),
    pg_provider VARCHAR(255),
    transaction_key VARCHAR(255),
    failure_reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6)
);

CREATE TABLE IF NOT EXISTS reconciliation_mismatch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(50),
    payment_id BIGINT,
    our_status VARCHAR(50),
    external_status VARCHAR(50),
    detected_at DATETIME(6),
    resolution VARCHAR(50),
    created_at DATETIME(6),
    updated_at DATETIME(6),
    note TEXT
);

CREATE TABLE IF NOT EXISTS product_metrics (
    product_id BIGINT NOT NULL,
    metric_date DATE NOT NULL,
    view_count INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    unlike_count INT NOT NULL DEFAULT 0,
    sales_count INT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    cancel_count_by_event_date INT NOT NULL DEFAULT 0,
    cancel_amount_by_event_date BIGINT NOT NULL DEFAULT 0,
    cancel_count_by_order_date INT NOT NULL DEFAULT 0,
    cancel_amount_by_order_date BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, metric_date),
    INDEX idx_metric_date (metric_date)
);

CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ranking INT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(8) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period_ranking (period_key, ranking)
);

CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    ranking INT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(8) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_period_ranking (period_key, ranking)
);

CREATE TABLE IF NOT EXISTS mv_product_rank_staging (
    product_id BIGINT NOT NULL,
    score DOUBLE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 0,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    period_key VARCHAR(8) NOT NULL,
    PRIMARY KEY (product_id, period_key)
);
