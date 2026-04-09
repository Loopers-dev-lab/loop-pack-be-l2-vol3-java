-- ============================================================
-- 랭킹 성능 비교 부하 테스트용 시딩 데이터
-- 상품 1,000건 + product_metrics 1,000건
-- ============================================================

-- 1. 브랜드 10개 (이미 있으면 무시)
INSERT IGNORE INTO brand (id, name, description, image_url, created_at, updated_at)
VALUES
    (1, 'Nike', 'Just Do It', 'https://example.com/nike.png', NOW(), NOW()),
    (2, 'Adidas', 'Impossible Is Nothing', 'https://example.com/adidas.png', NOW(), NOW()),
    (3, 'Puma', 'Forever Faster', 'https://example.com/puma.png', NOW(), NOW()),
    (4, 'New Balance', 'Fearlessly Independent', 'https://example.com/nb.png', NOW(), NOW()),
    (5, 'Converse', 'All Star', 'https://example.com/converse.png', NOW(), NOW()),
    (6, 'Vans', 'Off The Wall', 'https://example.com/vans.png', NOW(), NOW()),
    (7, 'Reebok', 'Be More Human', 'https://example.com/reebok.png', NOW(), NOW()),
    (8, 'Fila', 'Exploring', 'https://example.com/fila.png', NOW(), NOW()),
    (9, 'Under Armour', 'I Will', 'https://example.com/ua.png', NOW(), NOW()),
    (10, 'Asics', 'Sound Mind Sound Body', 'https://example.com/asics.png', NOW(), NOW());

-- 2. 상품 1,000건 생성 (프로시저)
DELIMITER //
DROP PROCEDURE IF EXISTS seed_products//
CREATE PROCEDURE seed_products()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 1000 DO
        INSERT IGNORE INTO product (id, brand_id, name, description, price, stock_quantity, image_url, like_count, created_at, updated_at)
        VALUES (
            i,
            (i % 10) + 1,
            CONCAT('상품_', LPAD(i, 4, '0')),
            CONCAT('테스트 상품 설명 #', i),
            FLOOR(10000 + RAND() * 90000),
            FLOOR(100 + RAND() * 900),
            CONCAT('https://example.com/product/', i, '.png'),
            FLOOR(RAND() * 500),
            NOW(),
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

CALL seed_products();
DROP PROCEDURE IF EXISTS seed_products;

-- 3. product_metrics 1,000건 생성 (프로시저)
DELIMITER //
DROP PROCEDURE IF EXISTS seed_metrics//
CREATE PROCEDURE seed_metrics()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE v_view_count BIGINT;
    DECLARE v_like_count BIGINT;
    DECLARE v_sales_count BIGINT;
    WHILE i <= 1000 DO
        SET v_view_count = FLOOR(100 + RAND() * 9900);
        SET v_like_count = FLOOR(10 + RAND() * 990);
        SET v_sales_count = FLOOR(1 + RAND() * 200);
        INSERT IGNORE INTO product_metrics (product_id, view_count, like_count, sales_count, sales_amount, version, updated_at)
        VALUES (
            i,
            v_view_count,
            v_like_count,
            v_sales_count,
            v_sales_count * FLOOR(10000 + RAND() * 90000),
            0,
            NOW()
        );
        SET i = i + 1;
    END WHILE;
END//
DELIMITER ;

CALL seed_metrics();
DROP PROCEDURE IF EXISTS seed_metrics;

-- ============================================================
-- Redis ZSET 시딩은 아래 스크립트로 별도 실행
-- 사용법: mysql -u root -p commerce < seed-ranking-data.sql
--         그 후 Redis 시딩 스크립트 실행
-- ============================================================
