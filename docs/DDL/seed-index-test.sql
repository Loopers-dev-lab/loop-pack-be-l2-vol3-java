-- =============================================================================
-- Loopers 인덱스 검증용 시드 데이터
-- 실행: mysql -u application -papplication loopers --default-character-set=utf8mb4 < docs/DDL/seed-index-test.sql
--
-- 삽입량:
--   brands             :     100건
--   products           : 100,000건 (브랜드당 1,000개)
--   product_stocks     : 100,000건 (products 1:1)
--   product_revisions  :  ~30,000건 (products당 0.3건 — CREATE 이력만)
--   users              :  10,000건
--   orders             : 100,000건 (PENDING 10% / CANCELLED 60% / EXPIRED 30%)
--   order_items        : 200,000건 (orders당 평균 2건)
--   likes              : 100,000건
--   cart_items         :  30,000건
--   coupons            :     500건
--   user_coupons       :  20,000건
--   order_cart_restore :  10,000건
--
-- ※ products 10만건 루프는 시간이 걸립니다 (약 1~3분 예상)
-- =============================================================================

USE loopers;

SET NAMES utf8mb4;
SET SESSION foreign_key_checks = 0;
SET SESSION unique_checks     = 0;

DROP PROCEDURE IF EXISTS seed_brands;
DROP PROCEDURE IF EXISTS seed_users;
DROP PROCEDURE IF EXISTS seed_products;
DROP PROCEDURE IF EXISTS seed_orders;
DROP PROCEDURE IF EXISTS seed_likes;
DROP PROCEDURE IF EXISTS seed_cart_items;
DROP PROCEDURE IF EXISTS seed_coupons;
DROP PROCEDURE IF EXISTS seed_order_cart_restore;

DELIMITER $$

-- ─────────────────────────────────────────────────────────────
-- 1. brands  100건
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_brands()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 100 DO
        INSERT INTO brands (brand_name, description, address, display_status, del_yn, created_at, updated_at)
        VALUES (
            CONCAT(ELT(MOD(i,10)+1,'감성','트렌디','모던','클래식','빈티지','미니멀','보헤미안','스포티','럭셔리','캐주얼'),
                   ' 브랜드 ', LPAD(i,4,'0')),
            CONCAT('브랜드 ', i, '의 공식 소개입니다.'),
            CONCAT('서울시 ', ELT(MOD(i,5)+1,'강남구','마포구','송파구','서초구','종로구'),
                   ' ', i, '번길 ', i*2),
            IF(i <= 80, 'ACTIVE', 'HIDDEN'),    -- 80% ACTIVE
            IF(i > 98, 'Y', 'N'),               -- 2% 소프트삭제
            NOW() - INTERVAL (100 - i) DAY,
            NOW() - INTERVAL (100 - i) DAY
        );
        SET i = i + 1;
    END WHILE;
END$$

-- ─────────────────────────────────────────────────────────────
-- 2. users  10,000건
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    WHILE i <= 10000 DO
        INSERT IGNORE INTO users (login_id, password, user_name, birthday, email, address, del_yn, created_at, updated_at)
        VALUES (
            CONCAT('user', LPAD(i,5,'0')),
            '$2a$10$fixedBCryptHashForSeedData00',
            CONCAT('사용자', i),
            CONCAT('19', LPAD(MOD(i,30)+70,2,'0'),
                   LPAD(MOD(i,12)+1,2,'0'),
                   LPAD(MOD(i,28)+1,2,'0')),
            CONCAT('user', LPAD(i,5,'0'), '@loopers.com'),
            CONCAT('서울시 ', ELT(MOD(i,5)+1,'강남구','마포구','송파구','서초구','종로구'),
                   ' 사용자로 ', i, '번지'),
            IF(i > 9900, 'Y', 'N'),            -- 1% 소프트삭제
            NOW() - INTERVAL (10000 - i) / 10 DAY,
            NOW() - INTERVAL (10000 - i) / 10 DAY
        );
        SET i = i + 1;
    END WHILE;
END$$

-- ─────────────────────────────────────────────────────────────
-- 3. products  100,000건 + product_stocks 1:1
--    브랜드 100개 × 상품 1,000개
--    + ~30,000건은 product_revisions (CREATE 이력)
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_products()
BEGIN
    DECLARE brand_cur  INT DEFAULT 1;
    DECLARE prod_cur   INT DEFAULT 1;
    DECLARE v_brand_id BIGINT;
    DECLARE last_pid   BIGINT;
    DECLARE total_prod INT DEFAULT 0;
    DECLARE p_display  VARCHAR(20);
    DECLARE p_sale     VARCHAR(20);
    DECLARE rv         DOUBLE;

    -- brands 테이블에서 실제 brand_id 를 순서대로 사용하기 위해 임시 테이블 활용
    DROP TEMPORARY TABLE IF EXISTS tmp_brand_ids;
    CREATE TEMPORARY TABLE tmp_brand_ids (seq INT AUTO_INCREMENT PRIMARY KEY, brand_id BIGINT);
    INSERT INTO tmp_brand_ids (brand_id) SELECT brand_id FROM brands ORDER BY brand_id;

    SET brand_cur = 1;
    WHILE brand_cur <= 100 DO
        SELECT brand_id INTO v_brand_id FROM tmp_brand_ids WHERE seq = brand_cur;

        SET prod_cur = 1;
        WHILE prod_cur <= 1000 DO
            SET total_prod = (brand_cur - 1) * 1000 + prod_cur;
            SET rv = RAND();

            SET p_display = IF(rv < 0.8, 'ACTIVE', 'HIDDEN');

            SET rv = RAND();
            SET p_sale = CASE
                WHEN rv < 0.70 THEN 'ON_SALE'
                WHEN rv < 0.90 THEN 'TEMP_SOLD_OUT'
                ELSE 'STOPPED'
            END;

            INSERT INTO products (
                brand_id, product_name, description, price,
                category, color, size,
                display_status, sale_status,
                revision_seq, like_count, del_yn, created_at, updated_at
            ) VALUES (
                v_brand_id,
                CONCAT(ELT(MOD(total_prod,8)+1,'봄','여름','가을','겨울','시즌리스','리미티드','스페셜','베이직'),
                       ' 상품 ', LPAD(total_prod,7,'0')),
                CONCAT(total_prod, '번째 상품입니다.'),
                (FLOOR(RAND() * 9890) + 10) * 100,
                ELT(MOD(prod_cur,6)+1,'상의','하의','아우터','원피스','신발','악세서리'),
                ELT(MOD(prod_cur,7)+1,'블랙','화이트','네이비','베이지','그레이','브라운','카키'),
                ELT(MOD(prod_cur,5)+1,'XS','S','M','L','XL'),
                p_display,
                p_sale,
                1,
                FLOOR(RAND() * 1000),
                IF(RAND() < 0.02, 'Y', 'N'),   -- 2% 소프트삭제
                NOW() - INTERVAL FLOOR(RAND() * 730) DAY,
                NOW() - INTERVAL FLOOR(RAND() * 30)  DAY
            );

            SET last_pid = LAST_INSERT_ID();

            -- product_stocks (1:1)
            INSERT INTO product_stocks (product_id, on_hand, reserved, created_at, updated_at)
            VALUES (last_pid, FLOOR(10 + RAND() * 990), 0, NOW(), NOW());

            -- product_revisions: 30%만 CREATE 이력 삽입 (~30,000건 목표)
            IF RAND() < 0.30 THEN
                INSERT INTO product_revisions (product_id, revision_seq, action, changed_by, change_reason, before_snapshot, after_snapshot, created_at)
                VALUES (last_pid, 1, 'CREATE', 'system', '상품 최초 등록', NULL,
                        JSON_OBJECT('product_name', CONCAT('상품 ', LPAD(total_prod,7,'0')),
                                    'price', (FLOOR(RAND()*9890)+10)*100,
                                    'sale_status', p_sale),
                        NOW() - INTERVAL FLOOR(RAND() * 730) DAY);
            END IF;

            SET prod_cur = prod_cur + 1;
        END WHILE;
        SET brand_cur = brand_cur + 1;
    END WHILE;

    DROP TEMPORARY TABLE IF EXISTS tmp_brand_ids;
END$$

-- ─────────────────────────────────────────────────────────────
-- 4. orders 100,000건 + order_items 200,000건
--    PENDING_PAYMENT 10% / CANCELLED 60% / EXPIRED 30%
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_orders()
BEGIN
    DECLARE i          INT     DEFAULT 1;
    DECLARE uid        BIGINT;
    DECLARE pid1       BIGINT;
    DECLARE pid2       BIGINT;
    DECLARE p_status   VARCHAR(20);
    DECLARE p_expires  DATETIME;
    DECLARE p_type     VARCHAR(20);
    DECLARE p_amount   DECIMAL(12,2);
    DECLARE oid        BIGINT;
    DECLARE rv         DOUBLE;
    DECLARE user_min   BIGINT;
    DECLARE user_max   BIGINT;
    DECLARE prod_min   BIGINT;
    DECLARE prod_max   BIGINT;

    SELECT MIN(user_id), MAX(user_id) INTO user_min, user_max FROM users  WHERE del_yn = 'N';
    SELECT MIN(product_id), MAX(product_id) INTO prod_min, prod_max FROM products WHERE del_yn = 'N';

    WHILE i <= 100000 DO
        SET rv = RAND();
        SET p_status = CASE
            WHEN rv < 0.10 THEN 'PENDING_PAYMENT'   -- 10%
            WHEN rv < 0.70 THEN 'CANCELLED'          -- 60%
            ELSE                 'EXPIRED'            -- 30%
        END;

        -- PENDING는 미래 만료, 나머지는 과거 만료
        SET p_expires = IF(p_status = 'PENDING_PAYMENT',
            NOW() + INTERVAL FLOOR(RAND() * 15)  MINUTE,
            NOW() - INTERVAL FLOOR(RAND() * 365) DAY);

        SET p_type = IF(RAND() < 0.6, 'DIRECT', 'CART');
        SET uid    = user_min + FLOOR(RAND() * (user_max - user_min + 1));
        SET p_amount = (FLOOR(RAND() * 9890) + 10) * 100;

        INSERT INTO orders (user_id, order_type, status, total_amount, expires_at, del_yn, created_at, updated_at)
        VALUES (uid, p_type, p_status, p_amount, p_expires, 'N',
                p_expires - INTERVAL 15 MINUTE,
                p_expires - INTERVAL 15 MINUTE);

        SET oid = LAST_INSERT_ID();

        -- order_items: 주문당 평균 2건 (1건 or 2건)
        SET pid1 = prod_min + FLOOR(RAND() * (prod_max - prod_min + 1));
        INSERT INTO order_items (order_id, order_item_seq, user_id, product_id, quantity,
                                 snapshot_product_name, snapshot_unit_price,
                                 del_yn, created_at, updated_at)
        VALUES (oid, 1, uid, pid1, FLOOR(RAND()*3)+1,
                CONCAT('상품스냅샷-', pid1), (FLOOR(RAND()*9890)+10)*100,
                'N', NOW() - INTERVAL FLOOR(RAND()*365) DAY, NOW());

        IF RAND() < 0.5 THEN
            SET pid2 = prod_min + FLOOR(RAND() * (prod_max - prod_min + 1));
            INSERT INTO order_items (order_id, order_item_seq, user_id, product_id, quantity,
                                     snapshot_product_name, snapshot_unit_price,
                                     del_yn, created_at, updated_at)
            VALUES (oid, 2, uid, pid2, FLOOR(RAND()*3)+1,
                    CONCAT('상품스냅샷-', pid2), (FLOOR(RAND()*9890)+10)*100,
                    'N', NOW() - INTERVAL FLOOR(RAND()*365) DAY, NOW());
        END IF;

        SET i = i + 1;
    END WHILE;
END$$

-- ─────────────────────────────────────────────────────────────
-- 5. likes  100,000건
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_likes()
BEGIN
    DECLARE i        INT DEFAULT 1;
    DECLARE uid      BIGINT;
    DECLARE pid      BIGINT;
    DECLARE user_min BIGINT;
    DECLARE user_max BIGINT;
    DECLARE prod_min BIGINT;
    DECLARE prod_max BIGINT;

    SELECT MIN(user_id), MAX(user_id) INTO user_min, user_max FROM users  WHERE del_yn = 'N';
    SELECT MIN(product_id), MAX(product_id) INTO prod_min, prod_max FROM products WHERE del_yn = 'N';

    WHILE i <= 100000 DO
        SET uid = user_min + FLOOR(RAND() * (user_max - user_min + 1));
        SET pid = prod_min + FLOOR(RAND() * (prod_max - prod_min + 1));
        INSERT IGNORE INTO likes (user_id, product_id, created_at)
        VALUES (uid, pid, NOW() - INTERVAL FLOOR(RAND()*365) DAY);
        SET i = i + 1;
    END WHILE;
END$$

-- ─────────────────────────────────────────────────────────────
-- 6. cart_items  30,000건
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_cart_items()
BEGIN
    DECLARE i        INT DEFAULT 1;
    DECLARE uid      BIGINT;
    DECLARE pid      BIGINT;
    DECLARE user_min BIGINT;
    DECLARE user_max BIGINT;
    DECLARE prod_min BIGINT;
    DECLARE prod_max BIGINT;

    SELECT MIN(user_id), MAX(user_id) INTO user_min, user_max FROM users  WHERE del_yn = 'N';
    SELECT MIN(product_id), MAX(product_id) INTO prod_min, prod_max FROM products WHERE del_yn = 'N';

    WHILE i <= 30000 DO
        SET uid = user_min + FLOOR(RAND() * (user_max - user_min + 1));
        SET pid = prod_min + FLOOR(RAND() * (prod_max - prod_min + 1));
        INSERT IGNORE INTO cart_items (user_id, product_id, quantity, created_at, updated_at)
        VALUES (uid, pid, FLOOR(RAND()*5)+1,
                NOW() - INTERVAL FLOOR(RAND()*90) DAY,
                NOW() - INTERVAL FLOOR(RAND()*30) DAY);
        SET i = i + 1;
    END WHILE;
END$$

-- ─────────────────────────────────────────────────────────────
-- 7. coupons 500건  +  user_coupons 20,000건
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_coupons()
BEGIN
    DECLARE i        INT DEFAULT 1;
    DECLARE uid      BIGINT;
    DECLARE cid      BIGINT;
    DECLARE user_min BIGINT;
    DECLARE user_max BIGINT;
    DECLARE coup_min BIGINT;
    DECLARE coup_max BIGINT;

    -- coupons 500건
    WHILE i <= 500 DO
        INSERT INTO coupons (name, type, value, min_order_amount, expired_at, del_yn, created_at, updated_at)
        VALUES (
            CONCAT(ELT(MOD(i,5)+1,'신규가입','시즌오프','생일축하','재구매','특별'), ' 할인쿠폰 ', LPAD(i,3,'0')),
            IF(MOD(i,2)=0, 'FIXED', 'RATE'),
            IF(MOD(i,2)=0, (MOD(i,10)+1)*1000, MOD(i,30)+5),
            IF(MOD(i,3)=0, NULL, (MOD(i,20)+1)*10000),
            NOW() + INTERVAL (MOD(i,24)+1) MONTH,
            IF(i > 490, 'Y', 'N'),
            NOW() - INTERVAL i DAY,
            NOW() - INTERVAL i DAY
        );
        SET i = i + 1;
    END WHILE;

    -- user_coupons 20,000건
    SELECT MIN(user_id), MAX(user_id)   INTO user_min, user_max FROM users   WHERE del_yn='N';
    SELECT MIN(coupon_id), MAX(coupon_id) INTO coup_min, coup_max FROM coupons WHERE del_yn='N';

    SET i = 1;
    WHILE i <= 20000 DO
        SET uid = user_min + FLOOR(RAND() * (user_max - user_min + 1));
        SET cid = coup_min + FLOOR(RAND() * (coup_max - coup_min + 1));
        INSERT IGNORE INTO user_coupons (user_id, coupon_id, status, issued_at, used_at, order_id)
        VALUES (uid, cid,
                ELT(FLOOR(RAND()*3)+1, 'AVAILABLE','USED','EXPIRED'),
                NOW() - INTERVAL FLOOR(RAND()*180) DAY,
                IF(RAND()<0.3, NOW() - INTERVAL FLOOR(RAND()*90) DAY, NULL),
                NULL);
        SET i = i + 1;
    END WHILE;
END$$

-- ─────────────────────────────────────────────────────────────
-- 8. order_cart_restore  10,000건
--    CANCELLED/EXPIRED + DIRECT 주문에서 샘플
-- ─────────────────────────────────────────────────────────────
CREATE PROCEDURE seed_order_cart_restore()
BEGIN
    DECLARE i   INT DEFAULT 0;
    DECLARE oid BIGINT;
    DECLARE uid BIGINT;

    -- DIRECT + CANCELLED/EXPIRED 주문 중 아직 복원 이력 없는 것 10,000건
    INSERT IGNORE INTO order_cart_restore (order_id, user_id, reason, trigger_source, restored_at)
    SELECT o.order_id, o.user_id,
           ELT(FLOOR(RAND()*2)+1,'USER_CANCELLED','EXPIRED'),
           ELT(FLOOR(RAND()*2)+1,'CANCEL_API','EXPIRE_JOB'),
           o.updated_at + INTERVAL 1 SECOND
    FROM orders o
    WHERE o.order_type = 'DIRECT'
      AND o.status IN ('CANCELLED', 'EXPIRED')
    ORDER BY RAND()
    LIMIT 10000;
END$$

DELIMITER ;

-- ─────────────────────────────────────────────────────────────
-- 실행 순서 (의존성 순)
-- ─────────────────────────────────────────────────────────────
SELECT NOW(), '1/8 브랜드 100건 시작' AS step;
CALL seed_brands();
SELECT NOW(), CONCAT('brands: ', COUNT(*), '건') AS result FROM brands;

SELECT NOW(), '2/8 유저 10,000건 시작' AS step;
CALL seed_users();
SELECT NOW(), CONCAT('users: ', COUNT(*), '건') AS result FROM users;

SELECT NOW(), '3/8 상품 100,000건 + 재고 + 이력 시작 (시간 소요)' AS step;
CALL seed_products();
SELECT NOW(), CONCAT('products: ',        COUNT(*), '건') AS result FROM products;
SELECT NOW(), CONCAT('product_stocks: ',  COUNT(*), '건') AS result FROM product_stocks;
SELECT NOW(), CONCAT('product_revisions: ', COUNT(*), '건') AS result FROM product_revisions;

SELECT NOW(), '4/8 주문 100,000건 + 주문항목 시작' AS step;
CALL seed_orders();
SELECT NOW(), CONCAT('orders: ',      COUNT(*), '건') AS result FROM orders;
SELECT NOW(), CONCAT('order_items: ', COUNT(*), '건') AS result FROM order_items;

SELECT NOW(), '5/8 좋아요 100,000건 시작' AS step;
CALL seed_likes();
SELECT NOW(), CONCAT('likes: ', COUNT(*), '건') AS result FROM likes;

SELECT NOW(), '6/8 장바구니 30,000건 시작' AS step;
CALL seed_cart_items();
SELECT NOW(), CONCAT('cart_items: ', COUNT(*), '건') AS result FROM cart_items;

SELECT NOW(), '7/8 쿠폰 500건 + 유저쿠폰 20,000건 시작' AS step;
CALL seed_coupons();
SELECT NOW(), CONCAT('coupons: ',      COUNT(*), '건') AS result FROM coupons;
SELECT NOW(), CONCAT('user_coupons: ', COUNT(*), '건') AS result FROM user_coupons;

SELECT NOW(), '8/8 장바구니 복원 이력 10,000건 시작' AS step;
CALL seed_order_cart_restore();
SELECT NOW(), CONCAT('order_cart_restore: ', COUNT(*), '건') AS result FROM order_cart_restore;

-- 정리
DROP PROCEDURE IF EXISTS seed_brands;
DROP PROCEDURE IF EXISTS seed_users;
DROP PROCEDURE IF EXISTS seed_products;
DROP PROCEDURE IF EXISTS seed_orders;
DROP PROCEDURE IF EXISTS seed_likes;
DROP PROCEDURE IF EXISTS seed_cart_items;
DROP PROCEDURE IF EXISTS seed_coupons;
DROP PROCEDURE IF EXISTS seed_order_cart_restore;

SET SESSION foreign_key_checks = 1;
SET SESSION unique_checks      = 1;

SELECT '=== 인덱스 검증용 시드 데이터 삽입 완료 ===' AS '';
