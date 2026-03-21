CREATE TABLE IF NOT EXISTS brands (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    image_url VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_brands_name (name)
);

CREATE TABLE IF NOT EXISTS categories (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_name (name)
);

CREATE TABLE IF NOT EXISTS members (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    password VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    birth_date DATE NOT NULL,
    phone VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_members_member_id (member_id)
);

CREATE TABLE IF NOT EXISTS point_balances (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    balance INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_point_balances_member_id (member_id)
);

CREATE TABLE IF NOT EXISTS products (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    stock INT NOT NULL,
    description VARCHAR(255),
    category_id BINARY(16) NOT NULL,
    brand_id BINARY(16) NOT NULL,
    like_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    KEY idx_products_category_id (category_id),
    KEY idx_products_brand_id (brand_id),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES brands (id)
);

CREATE TABLE IF NOT EXISTS coupons (
    id BINARY(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    value INT NOT NULL,
    min_order_amount INT NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS issued_coupons (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    coupon_id BINARY(16) NOT NULL,
    status VARCHAR(255) NOT NULL,
    issued_at DATETIME(6) NOT NULL,
    expired_at DATETIME(6) NOT NULL,
    used_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_issued_coupons_member_coupon (member_id, coupon_id)
);

CREATE TABLE IF NOT EXISTS orders (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    order_number VARCHAR(255) NOT NULL,
    order_date DATETIME(6) NOT NULL,
    status VARCHAR(255) NOT NULL,
    total_amount INT NOT NULL,
    coupon_id BINARY(16),
    used_point_amount INT NOT NULL,
    stock_deducted_at DATETIME(6),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_number (order_number)
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
    pg_transaction_key VARCHAR(255),
    reason VARCHAR(255),
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_member_order (member_id, order_id),
    UNIQUE KEY uk_payments_pg_transaction_key (pg_transaction_key)
);

CREATE TABLE IF NOT EXISTS likes (
    id BINARY(16) NOT NULL,
    member_id VARCHAR(255) NOT NULL,
    product_id BINARY(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    deleted_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_likes_member_product (member_id, product_id),
    KEY idx_likes_product_id (product_id)
);
