-- =============================================================================
-- V001 Rollback: 상품 좋아요 수 컬럼 및 인덱스 제거
-- =============================================================================

-- 1. 인덱스 제거
DROP INDEX idx_products_created_at ON products;
DROP INDEX idx_products_brand_price ON products;
DROP INDEX idx_products_brand_like ON products;
DROP INDEX idx_products_like_count ON products;

-- 2. 컬럼 제거
ALTER TABLE products DROP COLUMN like_count;
