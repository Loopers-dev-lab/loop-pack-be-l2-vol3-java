-- =============================================================
-- 대량 데이터 시딩 SQL (MySQL 8.0)
-- 브랜드 100개, 상품 100,000개, 재고 100,000개
-- =============================================================

-- 기존 데이터 정리
SET FOREIGN_KEY_CHECKS = 0;
TRUNCATE TABLE stock;
TRUNCATE TABLE product;
TRUNCATE TABLE brand;
SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
-- 1. 브랜드 100개 생성
-- =============================================================
DELIMITER $$

DROP PROCEDURE IF EXISTS seed_brands$$
CREATE PROCEDURE seed_brands()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE now_ts DATETIME(6);
    SET now_ts = NOW(6);

    WHILE i <= 100 DO
        INSERT INTO brand (name, description, created_at, updated_at, deleted_at)
        VALUES (
            CONCAT('Brand_', LPAD(i, 3, '0')),
            CONCAT('브랜드 ', i, ' 설명'),
            now_ts,
            now_ts,
            NULL
        );
        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL seed_brands();
DROP PROCEDURE IF EXISTS seed_brands;

-- =============================================================
-- 2. 상품 100,000개 생성 (브랜드당 약 1,000개)
--    - price: 1,000 ~ 1,000,000 랜덤
--    - like_count: 0 ~ 10,000 랜덤
--    - 배치 INSERT (1,000건씩)
-- =============================================================
DELIMITER $$

DROP PROCEDURE IF EXISTS seed_products$$
CREATE PROCEDURE seed_products()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_count INT DEFAULT 0;
    DECLARE brand_id_val BIGINT;
    DECLARE price_val INT;
    DECLARE like_val INT;
    DECLARE now_ts DATETIME(6);
    DECLARE sql_text LONGTEXT;

    SET now_ts = NOW(6);
    SET sql_text = '';

    WHILE i <= 100000 DO
        -- 브랜드 순환 배정: 1~100
        SET brand_id_val = ((i - 1) % 100) + 1;
        -- price: 1,000 ~ 1,000,000
        SET price_val = FLOOR(1000 + RAND() * 999001);
        -- like_count: 0 ~ 10,000
        SET like_val = FLOOR(RAND() * 10001);

        IF batch_count = 0 THEN
            SET sql_text = CONCAT(
                'INSERT INTO product (name, description, price, brand_id, like_count, created_at, updated_at, deleted_at) VALUES ',
                '(''', CONCAT('Product_', LPAD(i, 6, '0')), ''', ',
                '''', CONCAT('상품 ', i, ' 설명'), ''', ',
                price_val, ', ', brand_id_val, ', ', like_val, ', ',
                '''', now_ts, ''', ''', now_ts, ''', NULL)'
            );
        ELSE
            SET sql_text = CONCAT(sql_text,
                ', (''', CONCAT('Product_', LPAD(i, 6, '0')), ''', ',
                '''', CONCAT('상품 ', i, ' 설명'), ''', ',
                price_val, ', ', brand_id_val, ', ', like_val, ', ',
                '''', now_ts, ''', ''', now_ts, ''', NULL)'
            );
        END IF;

        SET batch_count = batch_count + 1;

        IF batch_count = 1000 OR i = 100000 THEN
            SET @dynamic_sql = sql_text;
            PREPARE stmt FROM @dynamic_sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SET batch_count = 0;
            SET sql_text = '';
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL seed_products();
DROP PROCEDURE IF EXISTS seed_products;

-- =============================================================
-- 3. 재고 100,000개 생성 (상품 1:1 대응)
--    - quantity: 0 ~ 500 랜덤
--    - 배치 INSERT (1,000건씩)
-- =============================================================
DELIMITER $$

DROP PROCEDURE IF EXISTS seed_stocks$$
CREATE PROCEDURE seed_stocks()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_count INT DEFAULT 0;
    DECLARE qty_val INT;
    DECLARE now_ts DATETIME(6);
    DECLARE sql_text LONGTEXT;

    SET now_ts = NOW(6);
    SET sql_text = '';

    WHILE i <= 100000 DO
        -- quantity: 0 ~ 500
        SET qty_val = FLOOR(RAND() * 501);

        IF batch_count = 0 THEN
            SET sql_text = CONCAT(
                'INSERT INTO stock (product_id, quantity, created_at, updated_at, deleted_at) VALUES ',
                '(', i, ', ', qty_val, ', ''', now_ts, ''', ''', now_ts, ''', NULL)'
            );
        ELSE
            SET sql_text = CONCAT(sql_text,
                ', (', i, ', ', qty_val, ', ''', now_ts, ''', ''', now_ts, ''', NULL)'
            );
        END IF;

        SET batch_count = batch_count + 1;

        IF batch_count = 1000 OR i = 100000 THEN
            SET @dynamic_sql = sql_text;
            PREPARE stmt FROM @dynamic_sql;
            EXECUTE stmt;
            DEALLOCATE PREPARE stmt;
            SET batch_count = 0;
            SET sql_text = '';
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL seed_stocks();
DROP PROCEDURE IF EXISTS seed_stocks;

-- =============================================================
-- 4. 시딩 결과 확인
-- =============================================================
SELECT '브랜드 수' AS label, COUNT(*) AS cnt FROM brand
UNION ALL
SELECT '상품 수', COUNT(*) FROM product
UNION ALL
SELECT '재고 수', COUNT(*) FROM stock;

SELECT brand_id, COUNT(*) AS product_count
FROM product
GROUP BY brand_id
ORDER BY brand_id
LIMIT 10;

-- =============================================================
-- 5. EXPLAIN 분석 쿼리
-- =============================================================

-- 5-1. 특정 브랜드의 인기순 상품 조회
EXPLAIN ANALYZE
SELECT * FROM product
WHERE brand_id = 1 AND deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20;

-- 5-2. 전체 인기순 상품 조회
EXPLAIN ANALYZE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY like_count DESC
LIMIT 20;

-- 5-3. 최신 상품 조회
EXPLAIN ANALYZE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY created_at DESC
LIMIT 20;

-- 5-4. 최저가 상품 조회
EXPLAIN ANALYZE
SELECT * FROM product
WHERE deleted_at IS NULL
ORDER BY price ASC
LIMIT 20;
