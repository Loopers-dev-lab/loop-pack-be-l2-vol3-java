-- ============================================================
-- 상품 더미 데이터 시드 스크립트 (10만 건)
-- 실행 전제: 앱을 먼저 실행해서 테이블이 생성된 상태여야 합니다.
-- 실행 방법: mysql -u application -papplication loopers < db/seed-products.sql
-- ============================================================

-- 브랜드 10개 삽입
INSERT INTO brand (name, description, created_at, updated_at)
VALUES
    ('나이키',   '글로벌 스포츠 브랜드',   NOW(), NOW()),
    ('아디다스', '독일 스포츠 브랜드',     NOW(), NOW()),
    ('뉴발란스', '미국 스포츠 브랜드',     NOW(), NOW()),
    ('컨버스',   '캐주얼 스니커즈 브랜드', NOW(), NOW()),
    ('반스',     '스케이트 브랜드',        NOW(), NOW()),
    ('푸마',     '독일 스포츠 브랜드',     NOW(), NOW()),
    ('리복',     '영국 스포츠 브랜드',     NOW(), NOW()),
    ('언더아머', '미국 퍼포먼스 브랜드',   NOW(), NOW()),
    ('살로몬',   '아웃도어 브랜드',        NOW(), NOW()),
    ('노스페이스','아웃도어 브랜드',        NOW(), NOW());

-- 상품 10만 건 삽입 (cross join으로 빠르게 생성)
SET @i = 0;

INSERT INTO product (name, ref_brand_id, price, stock, like_count, created_at, updated_at)
SELECT
    CONCAT('상품_', @i := @i + 1),
    FLOOR(1 + RAND() * 10),
    FLOOR(1 + RAND() * 100) * 1000,
    FLOOR(RAND() * 500),
    FLOOR(RAND() * 1000),
    DATE_SUB(NOW(), INTERVAL FLOOR(RAND() * 365) DAY),
    NOW()
FROM
    information_schema.columns a,
    information_schema.columns b
LIMIT 100000;
