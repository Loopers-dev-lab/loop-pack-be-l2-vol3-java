-- =============================================================================
-- 인덱스 성능 벤치마크 테스트
-- 상품 수: 10만, 20만, 50만 개 기준 측정
-- =============================================================================

-- 설정
SET @test_brand_id = 1;
SET @page_size = 20;

-- =============================================================================
-- 1. 테스트 데이터 생성 프로시저
-- =============================================================================
DROP PROCEDURE IF EXISTS create_test_products;
DELIMITER //
CREATE PROCEDURE create_test_products(IN product_count INT)
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_size INT DEFAULT 5000;
    DECLARE batch_count INT DEFAULT 0;
    DECLARE brand_count INT DEFAULT 50;

    SET FOREIGN_KEY_CHECKS = 0;
    SET autocommit = 0;
    SET unique_checks = 0;

    -- 기존 데이터 삭제
    TRUNCATE TABLE products;

    WHILE i <= product_count DO
        INSERT INTO products (brand_id, name, description, price, stock, image_url, like_count, created_at, updated_at, deleted_at)
        VALUES (
            (i % brand_count) + 1,
            CONCAT('Product-', i),
            CONCAT('Description for product ', i),
            FLOOR(1000 + RAND() * 500000),
            FLOOR(RAND() * 1000),
            CONCAT('https://cdn.example.com/products/', i, '.jpg'),
            -- like_count: Power law 분포 (상위 1%가 높은 좋아요)
            CASE
                WHEN RAND() < 0.01 THEN FLOOR(1000 + RAND() * 9000)  -- 상위 1%: 1000-10000
                WHEN RAND() < 0.10 THEN FLOOR(100 + RAND() * 900)    -- 상위 10%: 100-1000
                WHEN RAND() < 0.40 THEN FLOOR(10 + RAND() * 90)      -- 상위 40%: 10-100
                ELSE FLOOR(RAND() * 10)                               -- 나머지: 0-10
            END,
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 730) DAY),
            NOW(),
            NULL
        );

        SET batch_count = batch_count + 1;
        IF batch_count >= batch_size THEN
            COMMIT;
            SET batch_count = 0;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;

    SET FOREIGN_KEY_CHECKS = 1;
    SET autocommit = 1;
    SET unique_checks = 1;
END //
DELIMITER ;

-- =============================================================================
-- 2. 벤치마크 실행 프로시저
-- =============================================================================
DROP PROCEDURE IF EXISTS run_benchmark;
DELIMITER //
CREATE PROCEDURE run_benchmark(IN test_name VARCHAR(100))
BEGIN
    DECLARE start_time DATETIME(6);
    DECLARE end_time DATETIME(6);
    DECLARE exec_time_ms DECIMAL(10,3);
    DECLARE row_count INT;

    SELECT COUNT(*) INTO row_count FROM products;

    SELECT CONCAT('=== ', test_name, ' (', row_count, ' rows) ===') AS benchmark;

    -- ---------------------------------------------------------
    -- Test 1: 좋아요 순 정렬 (전체)
    -- ---------------------------------------------------------
    SET start_time = NOW(6);

    SELECT SQL_NO_CACHE id, name, like_count
    FROM products
    WHERE deleted_at IS NULL
    ORDER BY like_count DESC, created_at DESC
    LIMIT 20;

    SET end_time = NOW(6);
    SET exec_time_ms = TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000;
    SELECT 'Test 1: ORDER BY like_count DESC LIMIT 20' AS test, exec_time_ms AS 'time_ms';

    EXPLAIN SELECT SQL_NO_CACHE id, name, like_count
    FROM products
    WHERE deleted_at IS NULL
    ORDER BY like_count DESC, created_at DESC
    LIMIT 20;

    -- ---------------------------------------------------------
    -- Test 2: 브랜드별 조회
    -- ---------------------------------------------------------
    SET start_time = NOW(6);

    SELECT SQL_NO_CACHE id, name, price
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL
    LIMIT 20;

    SET end_time = NOW(6);
    SET exec_time_ms = TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000;
    SELECT 'Test 2: WHERE brand_id = ? LIMIT 20' AS test, exec_time_ms AS 'time_ms';

    EXPLAIN SELECT SQL_NO_CACHE id, name, price
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL
    LIMIT 20;

    -- ---------------------------------------------------------
    -- Test 3: 브랜드별 + 좋아요 순
    -- ---------------------------------------------------------
    SET start_time = NOW(6);

    SELECT SQL_NO_CACHE id, name, like_count
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL
    ORDER BY like_count DESC
    LIMIT 20;

    SET end_time = NOW(6);
    SET exec_time_ms = TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000;
    SELECT 'Test 3: WHERE brand_id = ? ORDER BY like_count DESC LIMIT 20' AS test, exec_time_ms AS 'time_ms';

    EXPLAIN SELECT SQL_NO_CACHE id, name, like_count
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL
    ORDER BY like_count DESC
    LIMIT 20;

    -- ---------------------------------------------------------
    -- Test 4: 브랜드별 + 가격순
    -- ---------------------------------------------------------
    SET start_time = NOW(6);

    SELECT SQL_NO_CACHE id, name, price
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL
    ORDER BY price ASC
    LIMIT 20;

    SET end_time = NOW(6);
    SET exec_time_ms = TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000;
    SELECT 'Test 4: WHERE brand_id = ? ORDER BY price ASC LIMIT 20' AS test, exec_time_ms AS 'time_ms';

    EXPLAIN SELECT SQL_NO_CACHE id, name, price
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL
    ORDER BY price ASC
    LIMIT 20;

    -- ---------------------------------------------------------
    -- Test 5: 페이지네이션 (깊은 페이지)
    -- ---------------------------------------------------------
    SET start_time = NOW(6);

    SELECT SQL_NO_CACHE id, name, like_count
    FROM products
    WHERE deleted_at IS NULL
    ORDER BY like_count DESC, created_at DESC
    LIMIT 20 OFFSET 10000;

    SET end_time = NOW(6);
    SET exec_time_ms = TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000;
    SELECT 'Test 5: ORDER BY like_count DESC LIMIT 20 OFFSET 10000' AS test, exec_time_ms AS 'time_ms';

    EXPLAIN SELECT SQL_NO_CACHE id, name, like_count
    FROM products
    WHERE deleted_at IS NULL
    ORDER BY like_count DESC, created_at DESC
    LIMIT 20 OFFSET 10000;

    -- ---------------------------------------------------------
    -- Test 6: COUNT 쿼리
    -- ---------------------------------------------------------
    SET start_time = NOW(6);

    SELECT SQL_NO_CACHE COUNT(*)
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL;

    SET end_time = NOW(6);
    SET exec_time_ms = TIMESTAMPDIFF(MICROSECOND, start_time, end_time) / 1000;
    SELECT 'Test 6: COUNT(*) WHERE brand_id = ?' AS test, exec_time_ms AS 'time_ms';

    EXPLAIN SELECT SQL_NO_CACHE COUNT(*)
    FROM products
    WHERE brand_id = @test_brand_id AND deleted_at IS NULL;

END //
DELIMITER ;

-- =============================================================================
-- 3. 인덱스 없이 테스트
-- =============================================================================
DROP PROCEDURE IF EXISTS test_without_indexes;
DELIMITER //
CREATE PROCEDURE test_without_indexes()
BEGIN
    DECLARE CONTINUE HANDLER FOR 1091 BEGIN END;  -- Index does not exist

    -- 인덱스 제거 (존재하면 삭제)
    ALTER TABLE products DROP INDEX idx_products_like_count;
    ALTER TABLE products DROP INDEX idx_products_brand_id;
    ALTER TABLE products DROP INDEX idx_products_brand_like;
    ALTER TABLE products DROP INDEX idx_products_brand_price;

    SELECT '>>> WITHOUT INDEXES <<<' AS mode;
    SHOW INDEX FROM products;

    CALL run_benchmark('Without Indexes');
END //
DELIMITER ;

-- =============================================================================
-- 4. 인덱스 있는 상태로 테스트
-- =============================================================================
DROP PROCEDURE IF EXISTS test_with_indexes;
DELIMITER //
CREATE PROCEDURE test_with_indexes()
BEGIN
    DECLARE CONTINUE HANDLER FOR 1061 BEGIN END;  -- Duplicate key name

    -- 인덱스 생성 (없으면 생성)
    CREATE INDEX idx_products_like_count ON products (like_count DESC, created_at DESC);
    CREATE INDEX idx_products_brand_id ON products (brand_id);
    CREATE INDEX idx_products_brand_like ON products (brand_id, like_count DESC);
    CREATE INDEX idx_products_brand_price ON products (brand_id, price ASC);

    -- 인덱스 통계 갱신
    ANALYZE TABLE products;

    SELECT '>>> WITH INDEXES <<<' AS mode;
    SHOW INDEX FROM products;

    CALL run_benchmark('With Indexes');
END //
DELIMITER ;

-- =============================================================================
-- 5. 전체 벤치마크 실행
-- =============================================================================
DROP PROCEDURE IF EXISTS run_full_benchmark;
DELIMITER //
CREATE PROCEDURE run_full_benchmark()
BEGIN
    -- 10만 개 테스트
    SELECT '############################################' AS sep;
    SELECT '### 100,000 Products ###' AS test_case;
    SELECT '############################################' AS sep;
    CALL create_test_products(100000);
    CALL test_without_indexes();
    CALL test_with_indexes();

    -- 20만 개 테스트
    SELECT '############################################' AS sep;
    SELECT '### 200,000 Products ###' AS test_case;
    SELECT '############################################' AS sep;
    CALL create_test_products(200000);
    CALL test_without_indexes();
    CALL test_with_indexes();

    -- 50만 개 테스트
    SELECT '############################################' AS sep;
    SELECT '### 500,000 Products ###' AS test_case;
    SELECT '############################################' AS sep;
    CALL create_test_products(500000);
    CALL test_without_indexes();
    CALL test_with_indexes();

    SELECT 'Benchmark Complete!' AS result;
END //
DELIMITER ;

-- =============================================================================
-- 실행 방법
-- =============================================================================
-- 전체 벤치마크 (10만/20만/50만):
--   CALL run_full_benchmark();
--
-- 개별 테스트:
--   CALL create_test_products(100000);
--   CALL test_without_indexes();
--   CALL test_with_indexes();
-- =============================================================================
