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
    KEY idx_products_category_reference_id (category_reference_id),
    KEY idx_products_brand_reference_id (brand_reference_id)
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
