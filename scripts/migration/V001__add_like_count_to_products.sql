-- =============================================================================
-- V001: 상품 좋아요 수 컬럼 및 인덱스 추가
-- =============================================================================

-- 1. 좋아요 수 컬럼 추가
ALTER TABLE products ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;

-- 2. 기존 좋아요 수로 초기값 설정
UPDATE products p
SET p.like_count = (
    SELECT COUNT(*)
    FROM likes l
    WHERE l.product_id = p.id
);

-- 3. 인덱스 추가
CREATE INDEX idx_products_like_count ON products (like_count DESC, created_at DESC);
CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC);
CREATE INDEX idx_products_brand_price ON products (brand_id, price ASC);
CREATE INDEX idx_products_created_at ON products (created_at DESC);
