-- =============================================================================
-- Dummy Data Insert Script
-- User: 1,000, Brand: 5,000, Product: 200,000, Order: 100,000, Coupon: 50,000
-- =============================================================================

SET @start_time = NOW();
SELECT CONCAT('Script started at: ', @start_time) AS info;

-- Disable foreign key checks and autocommit for faster inserts
SET FOREIGN_KEY_CHECKS = 0;
SET autocommit = 0;
SET unique_checks = 0;

-- =============================================================================
-- 1. USERS (1,000)
-- =============================================================================
DROP PROCEDURE IF EXISTS insert_users;
DELIMITER //
CREATE PROCEDURE insert_users()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_count INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 500;
    -- Pre-computed BCrypt hash for "Password1!" (cost=10)
    DECLARE bcrypt_hash VARCHAR(100) DEFAULT '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZRGdjGj/n3.rSuBsB63rP6IbKd3XO';
    DECLARE birth_year INT;
    DECLARE birth_month INT;
    DECLARE birth_day INT;
    DECLARE birth_date_str VARCHAR(8);

    WHILE i <= 1000 DO
        -- Generate random birth date (1970-2005)
        SET birth_year = 1970 + FLOOR(RAND() * 35);
        SET birth_month = 1 + FLOOR(RAND() * 12);
        SET birth_day = 1 + FLOOR(RAND() * 28);
        SET birth_date_str = CONCAT(
            LPAD(birth_year, 4, '0'),
            LPAD(birth_month, 2, '0'),
            LPAD(birth_day, 2, '0')
        );

        INSERT INTO users (login_id, password, name, birth_date, email, created_at, updated_at, deleted_at)
        VALUES (
            CONCAT('user', LPAD(i, 6, '0')),
            bcrypt_hash,
            CONCAT('User', i),
            birth_date_str,
            CONCAT('user', i, '@example.com'),
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
END //
DELIMITER ;

CALL insert_users();
SELECT CONCAT('Users inserted: ', (SELECT COUNT(*) FROM users)) AS progress;

-- =============================================================================
-- 2. BRANDS (5,000)
-- =============================================================================
DROP PROCEDURE IF EXISTS insert_brands;
DELIMITER //
CREATE PROCEDURE insert_brands()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE batch_count INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 500;

    WHILE i <= 5000 DO
        INSERT INTO brands (name, description, logo_url, created_at, updated_at, deleted_at)
        VALUES (
            CONCAT('Brand-', LPAD(i, 4, '0')),
            CONCAT('Brand ', i, ' - Premium quality products since ', 1950 + (i % 75)),
            CONCAT('https://cdn.example.com/logos/brand-', i, '.png'),
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY),
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
END //
DELIMITER ;

CALL insert_brands();
SELECT CONCAT('Brands inserted: ', (SELECT COUNT(*) FROM brands)) AS progress;

-- =============================================================================
-- 3. PRODUCTS (200,000) - Distributed across 5000 brands
-- =============================================================================
DROP PROCEDURE IF EXISTS insert_products;
DELIMITER //
CREATE PROCEDURE insert_products()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE brand_count INT DEFAULT 5000;
    DECLARE batch_size INT DEFAULT 5000;
    DECLARE batch_count INT DEFAULT 0;

    -- 200,000 products / 5,000 brands = 40 products per brand (average)
    WHILE i <= 200000 DO
        INSERT INTO products (brand_id, name, description, price, stock, image_url, like_count, created_at, updated_at, deleted_at)
        VALUES (
            ((i - 1) % brand_count) + 1,
            CONCAT('Product-', i),
            CONCAT('High quality product. Item number ', i, '. Perfect for everyday use.'),
            FLOOR(1000 + RAND() * 500000),
            FLOOR(RAND() * 1000),
            CONCAT('https://cdn.example.com/products/', i, '.jpg'),
            0,
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
END //
DELIMITER ;

CALL insert_products();
SELECT CONCAT('Products inserted: ', (SELECT COUNT(*) FROM products)) AS progress;

-- =============================================================================
-- 4. LIKES (variable per product for like_count variation)
-- =============================================================================
DROP PROCEDURE IF EXISTS insert_likes;
DELIMITER //
CREATE PROCEDURE insert_likes()
BEGIN
    DECLARE product_id INT DEFAULT 1;
    DECLARE max_product_id INT;
    DECLARE likes_count INT;
    DECLARE user_id INT;
    DECLARE j INT;
    DECLARE batch_count INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 10000;

    SELECT MAX(id) INTO max_product_id FROM products;

    WHILE product_id <= max_product_id DO
        -- Like distribution: Power law (few products have many likes)
        -- Top 1% products: 100-500 likes
        -- Next 9% products: 20-100 likes
        -- Next 30% products: 5-20 likes
        -- Remaining 60%: 0-5 likes

        SET @rand_val = RAND();

        IF @rand_val < 0.01 THEN
            SET likes_count = FLOOR(100 + RAND() * 400);
        ELSEIF @rand_val < 0.10 THEN
            SET likes_count = FLOOR(20 + RAND() * 80);
        ELSEIF @rand_val < 0.40 THEN
            SET likes_count = FLOOR(5 + RAND() * 15);
        ELSE
            SET likes_count = FLOOR(RAND() * 5);
        END IF;

        SET j = 1;
        WHILE j <= likes_count DO
            -- User IDs: 1 ~ 1000 (assuming 1k users)
            SET user_id = FLOOR(1 + RAND() * 1000);

            -- Ignore duplicate key errors
            INSERT IGNORE INTO likes (user_id, product_id, created_at)
            VALUES (
                user_id,
                product_id,
                DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)
            );

            SET batch_count = batch_count + 1;
            IF batch_count >= batch_size THEN
                COMMIT;
                SET batch_count = 0;
            END IF;

            SET j = j + 1;
        END WHILE;

        SET product_id = product_id + 1;
    END WHILE;

    COMMIT;
END //
DELIMITER ;

CALL insert_likes();
SELECT CONCAT('Likes inserted: ', (SELECT COUNT(*) FROM likes)) AS progress;

-- Update products.like_count with actual counts
UPDATE products p
SET p.like_count = (
    SELECT COUNT(*)
    FROM likes l
    WHERE l.product_id = p.id
);
COMMIT;
SELECT 'Products like_count updated' AS progress;

-- =============================================================================
-- 5. COUPON_TEMPLATES (50,000)
-- =============================================================================
DROP PROCEDURE IF EXISTS insert_coupons;
DELIMITER //
CREATE PROCEDURE insert_coupons()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE coupon_type VARCHAR(20);
    DECLARE batch_count INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 5000;

    WHILE i <= 50000 DO
        -- 60% FIXED, 40% RATE
        IF RAND() < 0.6 THEN
            SET coupon_type = 'FIXED';

            INSERT INTO coupon_templates (
                name, type, value, min_order_amount, max_discount_amount,
                max_issue_count, issued_count, expired_at, created_at, updated_at, deleted_at
            )
            VALUES (
                CONCAT('FixedCoupon-', i),
                coupon_type,
                -- Fixed discount: 1000, 2000, 3000, 5000, 10000
                ELT(FLOOR(1 + RAND() * 5), 1000, 2000, 3000, 5000, 10000),
                -- Min order amount: 10000 ~ 100000
                FLOOR(10000 + RAND() * 90000),
                NULL,
                -- Max issue count: NULL (unlimited) or 100~10000
                IF(RAND() < 0.3, NULL, FLOOR(100 + RAND() * 9900)),
                -- Issued count: 0 ~ max_issue_count/2
                FLOOR(RAND() * 500),
                -- Expired at: 1 day ~ 365 days from now
                DATE_ADD(NOW(), INTERVAL FLOOR(1 + RAND() * 365) DAY),
                DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 180) DAY),
                NOW(),
                NULL
            );
        ELSE
            SET coupon_type = 'RATE';

            INSERT INTO coupon_templates (
                name, type, value, min_order_amount, max_discount_amount,
                max_issue_count, issued_count, expired_at, created_at, updated_at, deleted_at
            )
            VALUES (
                CONCAT('RateCoupon-', i),
                coupon_type,
                -- Rate discount: 5, 10, 15, 20, 30 (percent)
                ELT(FLOOR(1 + RAND() * 5), 5, 10, 15, 20, 30),
                -- Min order amount: 20000 ~ 200000
                FLOOR(20000 + RAND() * 180000),
                -- Max discount amount: 5000 ~ 50000
                FLOOR(5000 + RAND() * 45000),
                -- Max issue count: NULL (unlimited) or 100~10000
                IF(RAND() < 0.3, NULL, FLOOR(100 + RAND() * 9900)),
                -- Issued count: 0 ~ 500
                FLOOR(RAND() * 500),
                -- Expired at: 1 day ~ 365 days from now
                DATE_ADD(NOW(), INTERVAL FLOOR(1 + RAND() * 365) DAY),
                DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 180) DAY),
                NOW(),
                NULL
            );
        END IF;

        SET batch_count = batch_count + 1;
        IF batch_count >= batch_size THEN
            COMMIT;
            SET batch_count = 0;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
END //
DELIMITER ;

CALL insert_coupons();
SELECT CONCAT('Coupons inserted: ', (SELECT COUNT(*) FROM coupon_templates)) AS progress;

-- =============================================================================
-- 6. ORDERS + ORDER_ITEMS (100,000 orders)
-- =============================================================================
DROP PROCEDURE IF EXISTS insert_orders;
DELIMITER //
CREATE PROCEDURE insert_orders()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE order_id INT;
    DECLARE user_id INT;
    DECLARE items_count INT;
    DECLARE j INT;
    DECLARE product_id INT;
    DECLARE product_name VARCHAR(200);
    DECLARE product_price BIGINT;
    DECLARE item_quantity INT;
    DECLARE order_total BIGINT;
    DECLARE max_product_id INT;
    DECLARE order_status VARCHAR(20);
    DECLARE batch_count INT DEFAULT 0;
    DECLARE batch_size INT DEFAULT 2000;

    SELECT MAX(id) INTO max_product_id FROM products;

    WHILE i <= 100000 DO
        -- User IDs: 1 ~ 1000
        SET user_id = FLOOR(1 + RAND() * 1000);

        -- Items per order: 1 ~ 5
        SET items_count = FLOOR(1 + RAND() * 5);

        -- Order status distribution
        SET @status_rand = RAND();
        IF @status_rand < 0.6 THEN
            SET order_status = 'COMPLETED';
        ELSEIF @status_rand < 0.8 THEN
            SET order_status = 'PENDING';
        ELSEIF @status_rand < 0.95 THEN
            SET order_status = 'SHIPPED';
        ELSE
            SET order_status = 'CANCELLED';
        END IF;

        SET order_total = 0;

        -- Insert order first with placeholder total
        INSERT INTO orders (user_id, total_price, original_amount, coupon_discount, point_discount, coupon_id, status, created_at)
        VALUES (
            user_id,
            0,
            0,
            0,
            0,
            NULL,
            order_status,
            DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY)
        );

        SET order_id = LAST_INSERT_ID();

        -- Insert order items
        SET j = 1;
        WHILE j <= items_count DO
            SET product_id = FLOOR(1 + RAND() * max_product_id);
            SET product_name = CONCAT('Product-', product_id);
            SET product_price = FLOOR(10000 + RAND() * 200000);
            SET item_quantity = FLOOR(1 + RAND() * 3);

            INSERT INTO order_items (order_id, product_id, product_name, quantity, price_snapshot)
            VALUES (order_id, product_id, product_name, item_quantity, product_price);

            SET order_total = order_total + (product_price * item_quantity);
            SET j = j + 1;
        END WHILE;

        -- Update order with actual total
        UPDATE orders SET total_price = order_total, original_amount = order_total WHERE id = order_id;

        SET batch_count = batch_count + 1;
        IF batch_count >= batch_size THEN
            COMMIT;
            SET batch_count = 0;
        END IF;

        SET i = i + 1;
    END WHILE;

    COMMIT;
END //
DELIMITER ;

CALL insert_orders();
SELECT CONCAT('Orders inserted: ', (SELECT COUNT(*) FROM orders)) AS progress;
SELECT CONCAT('Order items inserted: ', (SELECT COUNT(*) FROM order_items)) AS progress;

-- =============================================================================
-- Cleanup and Re-enable checks
-- =============================================================================
SET FOREIGN_KEY_CHECKS = 1;
SET autocommit = 1;
SET unique_checks = 1;

-- Drop procedures
DROP PROCEDURE IF EXISTS insert_users;
DROP PROCEDURE IF EXISTS insert_brands;
DROP PROCEDURE IF EXISTS insert_products;
DROP PROCEDURE IF EXISTS insert_likes;
DROP PROCEDURE IF EXISTS insert_coupons;
DROP PROCEDURE IF EXISTS insert_orders;

-- =============================================================================
-- Summary
-- =============================================================================
SELECT
    @start_time AS started_at,
    NOW() AS completed_at,
    TIMEDIFF(NOW(), @start_time) AS duration;

SELECT 'Data Summary:' AS info;
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'brands', COUNT(*) FROM brands
UNION ALL
SELECT 'products', COUNT(*) FROM products
UNION ALL
SELECT 'likes', COUNT(*) FROM likes
UNION ALL
SELECT 'coupon_templates', COUNT(*) FROM coupon_templates
UNION ALL
SELECT 'orders', COUNT(*) FROM orders
UNION ALL
SELECT 'order_items', COUNT(*) FROM order_items;

-- Like count distribution (using denormalized like_count column)
SELECT 'Like count distribution (top 20 products):' AS info;
SELECT id AS product_id, name, like_count
FROM products
ORDER BY like_count DESC
LIMIT 20;
