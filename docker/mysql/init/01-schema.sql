CREATE TABLE IF NOT EXISTS categories (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_reference_id (reference_id),
    UNIQUE KEY uk_categories_name (name)
);

CREATE TABLE IF NOT EXISTS brands (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    image_url VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brands_reference_id (reference_id),
    UNIQUE KEY uk_brands_name (name)
);

CREATE TABLE IF NOT EXISTS products (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference_id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    stock INT NOT NULL,
    description VARCHAR(255) NULL,
    category_reference_id BINARY(16) NOT NULL,
    brand_reference_id BINARY(16) NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_reference_id (reference_id),
    KEY idx_products_brand_deleted_like (brand_reference_id, deleted_at, like_count DESC, id),
    KEY idx_products_brand_deleted_created (brand_reference_id, deleted_at, created_at DESC, id),
    KEY idx_products_brand_deleted_price (brand_reference_id, deleted_at, price, id),
    KEY idx_products_brand_category_deleted_like (brand_reference_id, category_reference_id, deleted_at, like_count DESC, id),
    KEY idx_products_brand_category_deleted_created (brand_reference_id, category_reference_id, deleted_at, created_at DESC, id),
    KEY idx_products_brand_category_deleted_price (brand_reference_id, category_reference_id, deleted_at, price, id)
);

CREATE TABLE IF NOT EXISTS members (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    phone VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_member_id (member_id)
);

CREATE TABLE IF NOT EXISTS point_balances (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    balance INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_balances_member_id (member_id)
);

CREATE TABLE IF NOT EXISTS coupons (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    value INT NOT NULL,
    min_order_amount INT NOT NULL,
    total_quantity INT NOT NULL DEFAULT 1,
    remaining_quantity INT NOT NULL DEFAULT 1,
    expired_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS coupon_issue_requests (
    id BINARY(16) NOT NULL,
    request_id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    coupon_id BINARY(16) NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason VARCHAR(255) NULL,
    requested_at DATETIME(6) NOT NULL,
    processed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_coupon_issue_requests_request_id (request_id),
    KEY idx_coupon_issue_requests_member_requested (member_id, requested_at DESC),
    KEY idx_coupon_issue_requests_coupon_requested (coupon_id, requested_at DESC)
);

CREATE TABLE IF NOT EXISTS issued_coupons (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    coupon_id BINARY(16) NOT NULL,
    status VARCHAR(255) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_issued_coupons_member_coupon (member_id, coupon_id),
    KEY idx_issued_coupons_member_status (member_id, status),
    KEY idx_issued_coupons_expired_at (expired_at)
);

CREATE TABLE IF NOT EXISTS orders (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    order_number VARCHAR(255) NOT NULL,
    order_date DATETIME(6) NOT NULL,
    status VARCHAR(255) NOT NULL,
    total_amount INT NOT NULL,
    coupon_id BINARY(16) NULL,
    used_point_amount INT NOT NULL DEFAULT 0,
    stock_deducted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number),
    KEY idx_orders_member_order_date (member_id, order_date DESC, id),
    KEY idx_orders_status_order_date (status, order_date DESC, id)
);

CREATE TABLE IF NOT EXISTS order_items (
    id BINARY(16) NOT NULL,
    order_id BINARY(16) NOT NULL,
    product_id BINARY(16) NOT NULL,
    quantity INT NOT NULL,
    snapshot_product_name VARCHAR(255) NOT NULL,
    snapshot_price INT NOT NULL,
    snapshot_brand_name VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order_id (order_id),
    KEY idx_order_items_product_id (product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id)
);

CREATE TABLE IF NOT EXISTS payments (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    order_id BINARY(16) NOT NULL,
    card_type VARCHAR(255) NOT NULL,
    card_no VARCHAR(255) NOT NULL,
    amount INT NOT NULL,
    status VARCHAR(255) NOT NULL,
    pg_transaction_key VARCHAR(255) NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_member_order (member_id, order_id),
    UNIQUE KEY uk_payments_pg_transaction_key (pg_transaction_key),
    KEY idx_payments_order_id (order_id),
    KEY idx_payments_status (status)
);

CREATE TABLE IF NOT EXISTS likes (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    product_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_likes_member_product (member_id, product_id),
    KEY idx_likes_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS read_model_sync_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    operation_type VARCHAR(30) NOT NULL,
    payload_json TEXT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    KEY idx_read_model_sync_task_status_next_attempt_at (status, next_attempt_at),
    KEY idx_read_model_sync_task_aggregate (aggregate_type, aggregate_id)
);

CREATE TABLE IF NOT EXISTS behavior_metrics_daily (
    id BIGINT NOT NULL AUTO_INCREMENT,
    metric_date DATE NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    member_id VARCHAR(255) NOT NULL DEFAULT '',
    product_id VARCHAR(36) NOT NULL DEFAULT '',
    order_id VARCHAR(36) NOT NULL DEFAULT '',
    event_count BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_behavior_metrics_daily_dim (metric_date, action_type, member_id, product_id, order_id),
    KEY idx_behavior_metrics_daily_action_date (action_type, metric_date)
);

CREATE TABLE IF NOT EXISTS outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_key VARCHAR(255) NOT NULL,
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    acked_at DATETIME(6) NULL,
    last_error VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_event_id (event_id),
    KEY idx_outbox_event_status_next_attempt (status, next_attempt_at, id),
    KEY idx_outbox_event_aggregate (aggregate_type, aggregate_id)
);

CREATE TABLE IF NOT EXISTS product_metrics_daily (
    metric_date DATE NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (metric_date, product_id),
    KEY idx_product_metrics_daily_product_date (product_id, metric_date),
    KEY idx_product_metrics_daily_date (metric_date)
);

CREATE TABLE IF NOT EXISTS product_metrics_hourly (
    metric_hour DATETIME(0) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (metric_hour, product_id),
    KEY idx_product_metrics_hourly_product_hour (product_id, metric_hour),
    KEY idx_product_metrics_hourly_hour (metric_hour)
);

CREATE TABLE IF NOT EXISTS product_ranking_weekly_batch (
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    ranking_score DECIMAL(18,1) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (period_start_date, product_id),
    KEY idx_product_ranking_weekly_batch_start_date (period_start_date),
    KEY idx_product_ranking_weekly_batch_score (period_start_date, ranking_score)
);

CREATE TABLE IF NOT EXISTS product_ranking_monthly_batch (
    period_start_date DATE NOT NULL,
    period_end_date DATE NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    like_count BIGINT NOT NULL DEFAULT 0,
    sales_count BIGINT NOT NULL DEFAULT 0,
    sales_amount BIGINT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    ranking_score DECIMAL(18,1) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (period_start_date, product_id),
    KEY idx_product_ranking_monthly_batch_start_date (period_start_date),
    KEY idx_product_ranking_monthly_batch_score (period_start_date, ranking_score)
);
CREATE TABLE IF NOT EXISTS event_handled (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consumer_group VARCHAR(120) NOT NULL,
    event_id BINARY(16) NOT NULL,
    handled_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_handled_consumer_event (consumer_group, event_id),
    KEY idx_event_handled_handled_at (handled_at)
);

CREATE TABLE IF NOT EXISTS like_event_handled (
    id BIGINT NOT NULL AUTO_INCREMENT,
    consumer_group VARCHAR(120) NOT NULL,
    event_id BINARY(16) NOT NULL,
    handled_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_like_event_handled_consumer_event (consumer_group, event_id),
    KEY idx_like_event_handled_handled_at (handled_at)
);

CREATE TABLE IF NOT EXISTS order_event_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    total_amount INT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_event_log_event_id (event_id),
    KEY idx_order_event_log_order_id (order_id),
    KEY idx_order_event_log_occurred_at (occurred_at)
);

CREATE TABLE IF NOT EXISTS payment_event_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id BINARY(16) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    order_id VARCHAR(36) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    before_status VARCHAR(50) NULL,
    after_status VARCHAR(50) NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_event_log_event_id (event_id),
    KEY idx_payment_event_log_order_id (order_id),
    KEY idx_payment_event_log_occurred_at (occurred_at)
);

CREATE TABLE IF NOT EXISTS order_cancel_saga_progress (
    order_id BINARY(16) NOT NULL,
    coupon_done BIT(1) NOT NULL,
    point_done BIT(1) NOT NULL,
    stock_done BIT(1) NOT NULL,
    last_error VARCHAR(1000) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    PRIMARY KEY (order_id)
);

CREATE TABLE IF NOT EXISTS order_create_saga_progress (
    order_id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    coupon_id BINARY(16) NULL,
    order_amount INT NOT NULL,
    requested_point_amount INT NOT NULL,
    payment_amount INT NOT NULL,
    card_type VARCHAR(50) NOT NULL,
    card_no VARCHAR(255) NOT NULL,
    coupon_done BIT(1) NOT NULL,
    point_done BIT(1) NOT NULL,
    payment_requested BIT(1) NOT NULL,
    completed BIT(1) NOT NULL,
    compensated BIT(1) NOT NULL,
    last_error VARCHAR(1000) NULL,
    PRIMARY KEY (order_id)
);
