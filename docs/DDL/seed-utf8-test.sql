-- =============================================================================
-- Loopers UTF-8 인코딩 테스트용 시드 데이터
-- 각 테이블 100건씩 삽입 (한글 포함)
-- 실행: mysql -u application -papplication loopers < docs/DDL/seed-utf8-test.sql
-- =============================================================================

USE loopers;

SET NAMES utf8mb4;
SET foreign_key_checks = 0;

DROP PROCEDURE IF EXISTS seed_utf8_test;

DELIMITER $$

CREATE PROCEDURE seed_utf8_test()
BEGIN
    DECLARE i      INT DEFAULT 1;
    DECLARE offset_val INT DEFAULT 0;

    -- ──────────────────────────────────────────────────────────
    -- 1. users (100건)
    -- ──────────────────────────────────────────────────────────
    SET i = 1;
    WHILE i <= 100 DO
        INSERT IGNORE INTO users (login_id, password, user_name, birthday, email, address, del_yn, created_at, updated_at)
        VALUES (
            CONCAT('testuser', LPAD(i, 3, '0')),
            '$2a$10$dummyhashedpasswordvalue00',
            CONCAT('테스트유저', i),
            CONCAT('199', LPAD(MOD(i, 9) + 1, 1, '0'), '0', LPAD(MOD(i, 12) + 1, 2, '0'), LPAD(MOD(i, 28) + 1, 2, '0')),
            CONCAT('test', LPAD(i, 3, '0'), '@loopers.com'),
            CONCAT('서울시 ', ELT(MOD(i, 5) + 1, '강남구', '마포구', '송파구', '서초구', '종로구'), ' 테스트로 ', i, '번길 ', i * 3),
            'N',
            NOW() - INTERVAL i DAY,
            NOW() - INTERVAL i DAY
        );
        SET i = i + 1;
    END WHILE;

    -- ──────────────────────────────────────────────────────────
    -- 2. brands (100건)
    -- ──────────────────────────────────────────────────────────
    SET i = 1;
    WHILE i <= 100 DO
        INSERT INTO brands (brand_name, description, address, display_status, del_yn, created_at, updated_at)
        VALUES (
            CONCAT(ELT(MOD(i, 10) + 1, '감성', '트렌디', '모던', '클래식', '빈티지', '미니멀', '보헤미안', '스포티', '럭셔리', '캐주얼'), ' 브랜드 ', LPAD(i, 3, '0')),
            CONCAT('이 브랜드는 ', i, '번째 테스트 브랜드입니다. 한국 감성 이커머스를 대표합니다.'),
            CONCAT('서울시 ', ELT(MOD(i, 5) + 1, '강남구', '마포구', '송파구', '서초구', '종로구'), ' 브랜드로 ', i * 5, '번지'),
            IF(i <= 80, 'ACTIVE', 'HIDDEN'),
            'N',
            NOW() - INTERVAL i DAY,
            NOW() - INTERVAL i DAY
        );
        SET i = i + 1;
    END WHILE;

    -- ──────────────────────────────────────────────────────────
    -- 3. products + product_stocks (각 100건)
    -- ──────────────────────────────────────────────────────────
    SET i = 1;
    WHILE i <= 100 DO
        SET offset_val = MOD(i - 1, 100);
        INSERT INTO products (
            brand_id, product_name, description, price,
            category, color, size, display_status, sale_status,
            revision_seq, like_count, del_yn, created_at, updated_at
        )
        SELECT
            b.brand_id,
            CONCAT(ELT(MOD(i, 8) + 1, '봄', '여름', '가을', '겨울', '시즌리스', '리미티드', '스페셜', '베이직'), ' 컬렉션 상품 ', LPAD(i, 3, '0')),
            CONCAT(i, '번째 상품입니다. 고품질 소재와 세련된 디자인으로 제작된 감성 아이템입니다.'),
            (FLOOR(RAND() * 9890) + 10) * 100,
            ELT(MOD(i, 6) + 1, '상의', '하의', '아우터', '원피스', '신발', '악세서리'),
            ELT(MOD(i, 7) + 1, '블랙', '화이트', '네이비', '베이지', '그레이', '브라운', '카키'),
            ELT(MOD(i, 5) + 1, 'XS', 'S', 'M', 'L', 'XL'),
            IF(i <= 80, 'ACTIVE', 'HIDDEN'),
            CASE
                WHEN MOD(i, 10) < 7 THEN 'ON_SALE'
                WHEN MOD(i, 10) < 9 THEN 'TEMP_SOLD_OUT'
                ELSE 'STOPPED'
            END,
            0,
            FLOOR(RAND() * 500),
            'N',
            NOW() - INTERVAL i DAY,
            NOW() - INTERVAL i DAY
        FROM brands b
        ORDER BY b.brand_id
        LIMIT 1 OFFSET offset_val;

        INSERT INTO product_stocks (product_id, on_hand, reserved, created_at, updated_at)
        VALUES (LAST_INSERT_ID(), FLOOR(10 + RAND() * 490), 0, NOW(), NOW());

        SET i = i + 1;
    END WHILE;

    -- ──────────────────────────────────────────────────────────
    -- 4. coupons (100건)
    -- ──────────────────────────────────────────────────────────
    SET i = 1;
    WHILE i <= 100 DO
        INSERT INTO coupons (name, type, value, min_order_amount, expired_at, del_yn, created_at, updated_at)
        VALUES (
            CONCAT(ELT(MOD(i, 5) + 1, '신규가입', '시즌오프', '생일축하', '재구매', '특별') , ' 할인쿠폰 ', LPAD(i, 3, '0')),
            IF(MOD(i, 2) = 0, 'FIXED', 'RATE'),
            IF(MOD(i, 2) = 0,
               (MOD(i, 10) + 1) * 1000,          -- FIXED: 1,000원 ~ 10,000원
               MOD(i, 30) + 5                     -- RATE: 5% ~ 34%
            ),
            IF(MOD(i, 3) = 0, NULL, (MOD(i, 20) + 1) * 10000),
            NOW() + INTERVAL (MOD(i, 12) + 1) MONTH,
            IF(i > 95, 'Y', 'N'),
            NOW() - INTERVAL i DAY,
            NOW() - INTERVAL i DAY
        );
        SET i = i + 1;
    END WHILE;

    -- ──────────────────────────────────────────────────────────
    -- 5. user_coupons (100건, users ↔ coupons 교차)
    -- ──────────────────────────────────────────────────────────
    SET i = 1;
    WHILE i <= 100 DO
        SET offset_val = MOD(i - 1, 95);
        INSERT IGNORE INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
        SELECT
            u.user_id,
            c.coupon_id,
            ELT(MOD(i, 3) + 1, 'AVAILABLE', 'USED', 'EXPIRED'),
            NOW() - INTERVAL i DAY,
            IF(MOD(i, 3) = 1, NOW() - INTERVAL (i - 1) DAY, NULL),
            NULL
        FROM users u
        JOIN coupons c ON c.del_yn = 'N'
        ORDER BY u.user_id, c.coupon_id
        LIMIT 1 OFFSET offset_val;

        SET i = i + 1;
    END WHILE;

END$$

DELIMITER ;

CALL seed_utf8_test();

-- ──────────────────────────────────────────────────────────
-- 결과 확인
-- ──────────────────────────────────────────────────────────
SELECT '=== UTF-8 인코딩 테스트 결과 ===' AS '';
SELECT CONCAT('users        삽입: ', COUNT(*), '건') AS result FROM users;
SELECT CONCAT('brands       삽입: ', COUNT(*), '건') AS result FROM brands;
SELECT CONCAT('products     삽입: ', COUNT(*), '건') AS result FROM products;
SELECT CONCAT('product_stocks 삽입: ', COUNT(*), '건') AS result FROM product_stocks;
SELECT CONCAT('coupons      삽입: ', COUNT(*), '건') AS result FROM coupons;
SELECT CONCAT('user_coupons 삽입: ', COUNT(*), '건') AS result FROM user_coupons;

-- 한글 데이터 샘플 확인
SELECT '--- 유저 샘플 (한글) ---' AS '';
SELECT user_id, login_id, user_name, address FROM users LIMIT 3;

SELECT '--- 브랜드 샘플 (한글) ---' AS '';
SELECT brand_id, brand_name, description FROM brands LIMIT 3;

SELECT '--- 상품 샘플 (한글) ---' AS '';
SELECT product_id, product_name, category, color, size FROM products LIMIT 3;

SELECT '--- 쿠폰 샘플 (한글) ---' AS '';
SELECT coupon_id, name, type, value FROM coupons LIMIT 3;

SET foreign_key_checks = 1;
DROP PROCEDURE IF EXISTS seed_utf8_test;
