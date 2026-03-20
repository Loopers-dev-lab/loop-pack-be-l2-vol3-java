-- =============================================================
-- 상품 목록 조회 EXPLAIN 분석
-- =============================================================
-- 인덱스:
--   idx_products_brand_created : (deleted_at, brand_id, created_at DESC)
--   idx_products_brand_price   : (deleted_at, brand_id, price)
--   idx_products_brand_likes   : (deleted_at, brand_id, like_count DESC)

-- =============================================================
-- 1. 최신순 정렬 (RECENT) - brandId 필터 없음
-- =============================================================
EXPLAIN
SELECT *
FROM products
WHERE deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;

-- =============================================================
-- 2. 최신순 정렬 (RECENT) - brandId 필터 있음
-- 기대: idx_products_brand_created 사용, filesort 없음
-- =============================================================
EXPLAIN
SELECT *
FROM products
WHERE deleted_at IS NULL
  AND brand_id = 1
ORDER BY created_at DESC
LIMIT 20 OFFSET 0;

-- =============================================================
-- 3. 가격 오름차순 정렬 (PRICE_ASC) - brandId 필터 없음
-- =============================================================
EXPLAIN
SELECT *
FROM products
WHERE deleted_at IS NULL
ORDER BY price ASC
LIMIT 20 OFFSET 0;

-- =============================================================
-- 4. 가격 오름차순 정렬 (PRICE_ASC) - brandId 필터 있음
-- 기대: idx_products_brand_price 사용, filesort 없음
-- =============================================================
EXPLAIN
SELECT *
FROM products
WHERE deleted_at IS NULL
  AND brand_id = 1
ORDER BY price ASC
LIMIT 20 OFFSET 0;

-- =============================================================
-- 5. 좋아요 내림차순 정렬 (LIKES_DESC) - brandId 필터 없음
-- =============================================================
EXPLAIN
SELECT *
FROM products
WHERE deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20 OFFSET 0;

-- =============================================================
-- 6. 좋아요 내림차순 정렬 (LIKES_DESC) - brandId 필터 있음
-- 기대: idx_products_brand_likes 사용, filesort 없음
-- =============================================================
EXPLAIN
SELECT *
FROM products
WHERE deleted_at IS NULL
  AND brand_id = 1
ORDER BY like_count DESC
LIMIT 20 OFFSET 0;

-- =============================================================
-- 7. COUNT 쿼리 (페이징용)
-- =============================================================
EXPLAIN
SELECT COUNT(*)
FROM products
WHERE deleted_at IS NULL
  AND brand_id = 1;

-- =============================================================
-- 8. 단건 상세 조회 (PK)
-- 기대: PRIMARY KEY 사용 (const)
-- =============================================================
EXPLAIN
SELECT *
FROM products
WHERE id = 1;
