CREATE TABLE product_stocks (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT  NOT NULL,
    quantity   INT     NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    CONSTRAINT uk_product_stocks_product_id UNIQUE (product_id)
);

-- 기존 데이터 마이그레이션 (idempotent: 재실행 안전)
INSERT INTO product_stocks (product_id, quantity, created_at, updated_at)
SELECT p.id, p.stock, p.created_at, p.updated_at
FROM products p
LEFT JOIN product_stocks ps ON ps.product_id = p.id
WHERE p.deleted_at IS NULL AND ps.id IS NULL;
