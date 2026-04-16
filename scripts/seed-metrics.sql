-- ============================================================
-- product_metrics 테스트 데이터 적재 스크립트
-- (배치 주간/월간 랭킹 테스트용)
--
-- [실행 전 필수 조건]
--   1. seed-data.sql 먼저 실행 (brands, products 데이터 필요)
--   2. commerce-streamer 또는 commerce-api 가 한 번이라도 실행되어
--      product_metrics 테이블이 생성된 상태여야 함
--
-- [실행 방법]
--   docker exec -i docker-mysql-1 mysql -uapplication -papplication < scripts/seed-metrics.sql
-- ============================================================

USE loopers;

-- 기존 메트릭 초기화
TRUNCATE TABLE product_metrics;

-- 상품 ID 1~20에 임의의 메트릭 데이터 삽입
-- score = like * 0.2 + order * 0.7 + view * 0.1
INSERT INTO product_metrics (product_id, like_count, order_count, view_count, updated_at) VALUES
(1,  500,  300, 10000, NOW()),
(2,  800,  150,  8000, NOW()),
(3,  200,  500,  5000, NOW()),  -- order 많아서 높은 순위 예상
(4,  900,   50, 12000, NOW()),
(5,  100,  400,  3000, NOW()),
(6,  600,  250,  9000, NOW()),
(7,   50,  600,  2000, NOW()),  -- order 최다 → 1위 예상
(8,  750,  100,  7000, NOW()),
(9,  300,  350,  6000, NOW()),
(10, 450,  200,  4000, NOW()),
(11, 650,  180,  8500, NOW()),
(12, 120,  420,  3500, NOW()),
(13, 880,   80, 11000, NOW()),
(14, 230,  380,  5500, NOW()),
(15, 550,  270,  9500, NOW()),
(16,  80,  550,  1500, NOW()),
(17, 720,  130,  7500, NOW()),
(18, 390,  310,  6500, NOW()),
(19, 470,  220,  4500, NOW()),
(20, 610,  260,  9200, NOW());

-- 결과 확인 (예상 순위: score 내림차순)
SELECT
    product_id,
    like_count,
    order_count,
    view_count,
    ROUND(like_count * 0.2 + order_count * 0.7 + view_count * 0.1, 1) AS score
FROM product_metrics
ORDER BY score DESC;
