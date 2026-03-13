-- =============================================================
-- 상품 성능 테스트용 시드 데이터 (브랜드 20개, 상품 10만건)
-- =============================================================
-- 사전 조건: brands 테이블에 20개 브랜드가 있어야 합니다.
-- 이 스크립트는 Docker 로컬 환경(MySQL 8.0)에서 실행합니다.

-- 1. 브랜드 20개 생성
INSERT INTO brands (name, description, created_at, updated_at)
SELECT
    CONCAT('Brand_', LPAD(seq, 2, '0')),
    CONCAT('Brand_', LPAD(seq, 2, '0'), ' 설명'),
    NOW(),
    NOW()
FROM (
    SELECT ROW_NUMBER() OVER () AS seq
    FROM information_schema.columns
    LIMIT 20
) t;

-- 2. 상품 100,000건 생성 (브랜드별 약 5,000건)
-- price: 1,000 ~ 500,000 랜덤
-- stock_quantity: 0 ~ 10,000 랜덤
-- like_count: 0 ~ 5,000 랜덤
DROP PROCEDURE IF EXISTS seed_products;

DELIMITER //
CREATE PROCEDURE seed_products()
BEGIN
    DECLARE i INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 1000;
    DECLARE total INT DEFAULT 100000;
    DECLARE brand_count INT DEFAULT 20;

    -- 트랜잭션 단위로 1000건씩 INSERT
    WHILE i < total DO
        INSERT INTO products (brand_id, name, price, stock_quantity, description, like_count, version, created_at, updated_at)
        SELECT
            ((seq % brand_count) + 1) AS brand_id,
            CONCAT('Product_', LPAD(seq + 1, 6, '0')) AS name,
            ROUND(1000 + RAND() * 499000, 2) AS price,
            FLOOR(RAND() * 10001) AS stock_quantity,
            CONCAT('상품 설명 #', seq + 1) AS description,
            FLOOR(RAND() * 5001) AS like_count,
            0 AS version,
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY) AS created_at,
            NOW() AS updated_at
        FROM (
            SELECT @rownum := @rownum + 1 AS seq
            FROM information_schema.columns a
            CROSS JOIN information_schema.columns b
            CROSS JOIN (SELECT @rownum := i - 1) r
            LIMIT 1000
        ) t;

        SET i = i + batch_size;
    END WHILE;
END //
DELIMITER ;

CALL seed_products();
DROP PROCEDURE IF EXISTS seed_products;

-- 확인
SELECT COUNT(*) AS total_products FROM products;
SELECT brand_id, COUNT(*) AS cnt FROM products GROUP BY brand_id ORDER BY brand_id;
