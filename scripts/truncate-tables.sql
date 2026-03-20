-- =============================================================================
-- Truncate All Tables (for clean insert)
-- =============================================================================

SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE order_items;
TRUNCATE TABLE orders;
TRUNCATE TABLE likes;
TRUNCATE TABLE products;
TRUNCATE TABLE brands;
TRUNCATE TABLE coupon_templates;
TRUNCATE TABLE issued_coupons;
TRUNCATE TABLE user_points;
TRUNCATE TABLE users;

SET FOREIGN_KEY_CHECKS = 1;

SELECT 'All tables truncated' AS result;