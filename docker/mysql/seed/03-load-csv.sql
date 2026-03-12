DROP TEMPORARY TABLE IF EXISTS staging_categories;
CREATE TEMPORARY TABLE staging_categories (
    id BIGINT NOT NULL,
    reference_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    updated_at VARCHAR(32) NOT NULL,
    deleted_at VARCHAR(32) NULL
);

LOAD DATA LOCAL INFILE '/seed-data/categories-seed.csv'
INTO TABLE staging_categories
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\r\n'
IGNORE 1 LINES;

INSERT INTO categories (
    id,
    reference_id,
    name,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    s.id,
    UUID_TO_BIN(s.reference_id),
    s.name,
    STR_TO_DATE(s.created_at, '%Y-%m-%d %H:%i:%s.%f'),
    STR_TO_DATE(s.updated_at, '%Y-%m-%d %H:%i:%s.%f'),
    IF(s.deleted_at = '\\N', NULL, STR_TO_DATE(s.deleted_at, '%Y-%m-%d %H:%i:%s.%f'))
FROM staging_categories s
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    updated_at = VALUES(updated_at),
    deleted_at = VALUES(deleted_at);

DROP TEMPORARY TABLE IF EXISTS staging_brands;
CREATE TEMPORARY TABLE staging_brands (
    id BIGINT NOT NULL,
    reference_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(255) NULL,
    image_url VARCHAR(255) NULL,
    created_at VARCHAR(32) NOT NULL,
    updated_at VARCHAR(32) NOT NULL,
    deleted_at VARCHAR(32) NULL
);

LOAD DATA LOCAL INFILE '/seed-data/brands-seed.csv'
INTO TABLE staging_brands
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\r\n'
IGNORE 1 LINES;

INSERT INTO brands (
    id,
    reference_id,
    name,
    description,
    image_url,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    s.id,
    UUID_TO_BIN(s.reference_id),
    s.name,
    NULLIF(s.description, '\\N'),
    NULLIF(s.image_url, '\\N'),
    STR_TO_DATE(s.created_at, '%Y-%m-%d %H:%i:%s.%f'),
    STR_TO_DATE(s.updated_at, '%Y-%m-%d %H:%i:%s.%f'),
    IF(s.deleted_at = '\\N', NULL, STR_TO_DATE(s.deleted_at, '%Y-%m-%d %H:%i:%s.%f'))
FROM staging_brands s
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    description = VALUES(description),
    image_url = VALUES(image_url),
    updated_at = VALUES(updated_at),
    deleted_at = VALUES(deleted_at);

DROP TEMPORARY TABLE IF EXISTS staging_products;
CREATE TEMPORARY TABLE staging_products (
    id BIGINT NOT NULL,
    reference_id CHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    price INT NOT NULL,
    stock INT NOT NULL,
    description VARCHAR(255) NULL,
    category_reference_id CHAR(36) NOT NULL,
    brand_reference_id CHAR(36) NOT NULL,
    like_count INT NOT NULL,
    created_at VARCHAR(32) NOT NULL,
    updated_at VARCHAR(32) NOT NULL,
    deleted_at VARCHAR(32) NULL
);

LOAD DATA LOCAL INFILE '/seed-data/products-seed-300000.csv'
INTO TABLE staging_products
CHARACTER SET utf8mb4
FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"'
LINES TERMINATED BY '\r\n'
IGNORE 1 LINES;

INSERT INTO products (
    id,
    reference_id,
    name,
    price,
    stock,
    description,
    category_reference_id,
    brand_reference_id,
    like_count,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    s.id,
    UUID_TO_BIN(s.reference_id),
    s.name,
    s.price,
    s.stock,
    NULLIF(s.description, '\\N'),
    UUID_TO_BIN(s.category_reference_id),
    UUID_TO_BIN(s.brand_reference_id),
    s.like_count,
    STR_TO_DATE(s.created_at, '%Y-%m-%d %H:%i:%s.%f'),
    STR_TO_DATE(s.updated_at, '%Y-%m-%d %H:%i:%s.%f'),
    IF(s.deleted_at = '\\N', NULL, STR_TO_DATE(s.deleted_at, '%Y-%m-%d %H:%i:%s.%f'))
FROM staging_products s
ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    price = VALUES(price),
    stock = VALUES(stock),
    description = VALUES(description),
    category_reference_id = VALUES(category_reference_id),
    brand_reference_id = VALUES(brand_reference_id),
    like_count = VALUES(like_count),
    updated_at = VALUES(updated_at),
    deleted_at = VALUES(deleted_at);

SELECT COUNT(*) AS categories_count FROM categories;
SELECT COUNT(*) AS brands_count FROM brands;
SELECT COUNT(*) AS products_count FROM products;
