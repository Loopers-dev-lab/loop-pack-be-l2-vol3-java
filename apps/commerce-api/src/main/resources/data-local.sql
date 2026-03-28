INSERT IGNORE INTO brands (brand_id, brand_name, created_at, updated_at)
VALUES ('B0000001', 'k6-brand', NOW(), NOW());

INSERT IGNORE INTO products (product_id, ref_brand_id, product_name, price, stock_quantity, created_at, updated_at)
SELECT v.product_id, b.id, v.product_name, v.price, v.stock_quantity, NOW(), NOW()
FROM (
    SELECT 'k6prod01' AS product_id, 'k6 상품 01' AS product_name, 10000.00 AS price, 999999 AS stock_quantity
    UNION ALL
    SELECT 'k6prod02', 'k6 상품 02', 10000.00, 999999
    UNION ALL
    SELECT 'k6prod03', 'k6 상품 03', 10000.00, 999999
    UNION ALL
    SELECT 'k6prod04', 'k6 상품 04', 10000.00, 999999
    UNION ALL
    SELECT 'k6prod05', 'k6 상품 05', 10000.00, 999999
) v
JOIN brands b ON b.brand_id = 'B0000001';
