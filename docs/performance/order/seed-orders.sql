-- =============================================================
-- 주문 성능 테스트용 시드 데이터
-- 주문 500,000건, 주문 아이템 ~1,500,000건
-- =============================================================
-- 사전 조건: products 테이블에 상품 데이터가 있어야 합니다.
--           (seed-products.sql 실행 후 사용)
-- 이 스크립트는 Docker 로컬 환경(MySQL 8.0)에서 실행합니다.

-- =============================================================
-- 1. 주문 500,000건 생성
-- =============================================================
-- 유저: 10,000명 (user_id: 1~10,000)
-- 상태별 분포:
--   DELIVERED  65% (325,000건) - 2주전 ~ 1년전
--   CANCELLED  25% (125,000건) - 전 기간
--   SHIPPING    5%  (25,000건) - 최근 2주
--   PAID        3%  (15,000건) - 최근 2~3일
--   PENDING     2%  (10,000건) - 최근 수시간
--
-- 유저별 주문 분포 (파레토):
--   상위 1%   (100명):  200~500건
--   상위 10% (1,000명):  50~200건
--   나머지   (9,000명):   1~50건

DROP PROCEDURE IF EXISTS seed_orders;

DELIMITER //
CREATE PROCEDURE seed_orders()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 1000;
    DECLARE total INT DEFAULT 500000;
    DECLARE user_count INT DEFAULT 10000;

    WHILE i < total DO
        INSERT INTO orders (user_id, status, total_amount, discount_amount, final_amount, issued_coupon_id, created_at)
        SELECT
            -- 파레토 분포: 상위 1%가 많은 주문
            CASE
                WHEN RAND() < 0.30 THEN FLOOR(RAND() * 100) + 1          -- 상위 1% (100명) → 30% 주문
                WHEN RAND() < 0.60 THEN FLOOR(RAND() * 900) + 101        -- 상위 10% (900명) → 30% 주문
                ELSE FLOOR(RAND() * 9000) + 1001                          -- 나머지 (9,000명) → 40% 주문
            END AS user_id,
            -- 상태별 분포
            CASE
                WHEN RAND() < 0.02 THEN 'PENDING'
                WHEN RAND() < 0.05 THEN 'PAID'
                WHEN RAND() < 0.10 THEN 'SHIPPING'
                WHEN RAND() < 0.35 THEN 'CANCELLED'
                ELSE 'DELIVERED'
            END AS status,
            ROUND(10000 + RAND() * 490000, 2) AS total_amount,
            ROUND(RAND() * 50000, 2) AS discount_amount,
            ROUND(10000 + RAND() * 440000, 2) AS final_amount,
            IF(RAND() < 0.3, FLOOR(RAND() * 1000) + 1, NULL) AS issued_coupon_id,
            -- 시간대별 분포
            CASE
                WHEN RAND() < 0.02 THEN DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 6) HOUR)                     -- PENDING: 최근 수시간
                WHEN RAND() < 0.05 THEN DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 3) DAY)                      -- PAID: 최근 2~3일
                WHEN RAND() < 0.10 THEN DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 14) DAY)                     -- SHIPPING: 최근 2주
                ELSE DATE_SUB(NOW(), INTERVAL FLOOR(14 + RAND() * 351) DAY)                                   -- DELIVERED/CANCELLED: 2주~1년전
            END AS created_at
        FROM (
            SELECT @rownum := @rownum + 1 AS seq
            FROM information_schema.columns a
            CROSS JOIN information_schema.columns b
            CROSS JOIN (SELECT @rownum := 0) r
            LIMIT 1000
        ) t;

        SET i = i + batch_size;
    END WHILE;
END //
DELIMITER ;

CALL seed_orders();
DROP PROCEDURE IF EXISTS seed_orders;

-- =============================================================
-- 2. 주문 아이템 생성 (주문당 3개 고정, 배치 INSERT)
-- =============================================================
-- CURSOR 방식은 50만건 × 서브쿼리로 수시간 소요.
-- 배치 INSERT로 orders.id 범위를 1000건씩 잡아 한 번에 3000건 INSERT.
-- 50만 주문 × 3개 = 150만건, 10분 이내 목표.

DROP PROCEDURE IF EXISTS seed_order_items;

DELIMITER //
CREATE PROCEDURE seed_order_items()
BEGIN
    DECLARE v_min_id BIGINT;
    DECLARE v_max_id BIGINT;
    DECLARE v_current BIGINT;
    DECLARE v_batch_end BIGINT;
    DECLARE batch_size INT DEFAULT 1000;
    DECLARE product_count INT DEFAULT 5000;

    SELECT MIN(id), MAX(id) INTO v_min_id, v_max_id FROM orders;
    SET v_current = v_min_id;

    WHILE v_current <= v_max_id DO
        SET v_batch_end = LEAST(v_current + batch_size - 1, v_max_id);

        -- 주문당 아이템 3개를 한 번에 INSERT (아이템 1번)
        INSERT INTO order_item (order_id, product_id, product_name, price, quantity, created_at)
        SELECT
            o.id,
            FLOOR(1 + RAND() * product_count),
            CONCAT('Product_', LPAD(FLOOR(1 + RAND() * product_count), 6, '0')),
            ROUND(1000 + RAND() * 99000, 2),
            FLOOR(1 + RAND() * 5),
            o.created_at
        FROM orders o
        WHERE o.id BETWEEN v_current AND v_batch_end;

        -- 아이템 2번
        INSERT INTO order_item (order_id, product_id, product_name, price, quantity, created_at)
        SELECT
            o.id,
            FLOOR(1 + RAND() * product_count),
            CONCAT('Product_', LPAD(FLOOR(1 + RAND() * product_count), 6, '0')),
            ROUND(1000 + RAND() * 99000, 2),
            FLOOR(1 + RAND() * 5),
            o.created_at
        FROM orders o
        WHERE o.id BETWEEN v_current AND v_batch_end;

        -- 아이템 3번
        INSERT INTO order_item (order_id, product_id, product_name, price, quantity, created_at)
        SELECT
            o.id,
            FLOOR(1 + RAND() * product_count),
            CONCAT('Product_', LPAD(FLOOR(1 + RAND() * product_count), 6, '0')),
            ROUND(1000 + RAND() * 99000, 2),
            FLOOR(1 + RAND() * 5),
            o.created_at
        FROM orders o
        WHERE o.id BETWEEN v_current AND v_batch_end;

        SET v_current = v_current + batch_size;
    END WHILE;
END //
DELIMITER ;

CALL seed_order_items();
DROP PROCEDURE IF EXISTS seed_order_items;

-- =============================================================
-- 3. 확인 쿼리
-- =============================================================
SELECT COUNT(*) AS total_orders FROM orders;
SELECT status, COUNT(*) AS cnt FROM orders GROUP BY status ORDER BY cnt DESC;
SELECT COUNT(*) AS total_order_items FROM order_item;

-- 유저별 주문 수 분포
SELECT
    CASE
        WHEN cnt >= 200 THEN '200+'
        WHEN cnt >= 50 THEN '50~199'
        WHEN cnt >= 10 THEN '10~49'
        ELSE '1~9'
    END AS order_range,
    COUNT(*) AS user_count
FROM (SELECT user_id, COUNT(*) AS cnt FROM orders GROUP BY user_id) t
GROUP BY order_range
ORDER BY MIN(cnt) DESC;
