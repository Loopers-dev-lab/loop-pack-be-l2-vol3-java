-- =============================================================================
-- Loopers 테스트 데이터 시딩 — MySQL Stored Procedure
-- 브랜드 100개 + 상품 10만건 + 재고 10만건
-- 실행: mysql -u application -papplication loopers < docs/DDL/seed-procedure.sql
-- =============================================================================

USE loopers;

DROP PROCEDURE IF EXISTS seed_brands;
DROP PROCEDURE IF EXISTS seed_products;

DELIMITER $$

-- ─────────────────────────────────────────
-- 브랜드 100개 삽입
-- ─────────────────────────────────────────
CREATE PROCEDURE seed_brands()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 100 DO
        INSERT INTO brands (brand_name, description, address, display_status, del_yn, created_at, updated_at)
        VALUES (
            CONCAT('Brand-', LPAD(i, 3, '0')),
            CONCAT('브랜드 ', i, '의 소개글입니다.'),
            CONCAT('서울시 강남구 테헤란로 ', i * 10, '길'),
            'ACTIVE',
            'N',
            NOW(), NOW()
        );
        SET i = i + 1;
    END WHILE;
END$$

-- ─────────────────────────────────────────
-- 상품 10만건 + 재고 삽입
--   · brand_id: 자동 감지 (brands 테이블 기준)
--   · display_status: ACTIVE 80% / HIDDEN 20%
--   · sale_status: ON_SALE 70% / TEMP_SOLD_OUT 20% / STOPPED 10%
--   · like_count: 0~999 균등 분포 (옵티마이저 선택성 확보)
--   · price: 1,000원 ~ 990,000원 (100원 단위)
-- ─────────────────────────────────────────
CREATE PROCEDURE seed_products()
BEGIN
    DECLARE brand_min  BIGINT DEFAULT 0;
    DECLARE brand_max  BIGINT DEFAULT 0;
    DECLARE brand_cnt  INT    DEFAULT 0;
    DECLARE i          INT    DEFAULT 1;
    DECLARE total      INT    DEFAULT 100000;  -- 총 삽입 상품 수
    DECLARE bid        BIGINT;
    DECLARE p_price    DECIMAL(12,2);
    DECLARE p_display  VARCHAR(20);
    DECLARE p_sale     VARCHAR(20);
    DECLARE p_like     BIGINT;
    DECLARE rand_val   DOUBLE;
    DECLARE last_pid   BIGINT;

    SELECT MIN(brand_id), MAX(brand_id), COUNT(*) INTO brand_min, brand_max, brand_cnt FROM brands;

    IF brand_cnt = 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'brands 테이블이 비어있습니다. seed_brands()를 먼저 실행하세요.';
    END IF;

    SET SESSION foreign_key_checks = 0;

    WHILE i <= total DO
        -- 브랜드 랜덤 배정
        SET bid = brand_min + FLOOR(RAND() * brand_cnt);

        -- 가격: 1,000 ~ 990,000원 (100원 단위)
        SET p_price = (FLOOR(RAND() * 9890) + 10) * 100;

        -- display_status: 80% ACTIVE / 20% HIDDEN
        SET rand_val = RAND();
        SET p_display = IF(rand_val < 0.8, 'ACTIVE', 'HIDDEN');

        -- sale_status: 70% ON_SALE / 20% TEMP_SOLD_OUT / 10% STOPPED
        SET rand_val = RAND();
        SET p_sale = CASE
            WHEN rand_val < 0.70 THEN 'ON_SALE'
            WHEN rand_val < 0.90 THEN 'TEMP_SOLD_OUT'
            ELSE 'STOPPED'
        END;

        -- like_count: 0~999 (롱테일 효과는 데이터 크기로 자연 발생)
        SET p_like = FLOOR(RAND() * 1000);

        INSERT INTO products (
            brand_id, product_name, description, price,
            display_status, sale_status, revision_seq, like_count,
            del_yn, created_at, updated_at
        ) VALUES (
            bid,
            CONCAT('상품-', LPAD(i, 6, '0')),
            CONCAT(i, '번째 상품입니다.'),
            p_price,
            p_display,
            p_sale,
            0,
            p_like,
            'N',
            NOW() - INTERVAL FLOOR(RAND() * 365) DAY,
            NOW()
        );

        SET last_pid = LAST_INSERT_ID();

        INSERT INTO product_stocks (product_id, on_hand, reserved, created_at, updated_at)
        VALUES (last_pid, FLOOR(10 + RAND() * 990), 0, NOW(), NOW());

        SET i = i + 1;
    END WHILE;

    SET SESSION foreign_key_checks = 1;
END$$

DELIMITER ;

-- ─────────────────────────────────────────
-- 실행
-- ─────────────────────────────────────────
CALL seed_brands();
SELECT CONCAT('브랜드 삽입 완료: ', COUNT(*), '개') AS result FROM brands;

CALL seed_products();
SELECT CONCAT('상품 삽입 완료: ', COUNT(*), '개') AS result FROM products;
SELECT CONCAT('재고 삽입 완료: ', COUNT(*), '개') AS result FROM product_stocks;

DROP PROCEDURE IF EXISTS seed_brands;
DROP PROCEDURE IF EXISTS seed_products;
