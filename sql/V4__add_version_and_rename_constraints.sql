-- =============================================================
-- 동시성 처리 리팩토링 DDL 마이그레이션
-- 실행 환경: dev / qa / prd (ddl-auto: none)
-- local / test는 ddl-auto: create로 자동 생성되므로 실행 불필요
-- =============================================================

-- [실행 가이드]
-- 1. 실행 전 반드시 백업
-- 2. Part 1 → Part 2 순서로 실행 (Part 1이 실패하면 Part 2를 실행하지 마세요)
-- 3. 각 ALTER TABLE은 개별 실행 가능 (부분 실행 시 실행된 항목 기록)
-- 4. 롤백 시: Part 2 역순 → Part 1 역순으로 DROP COLUMN / DROP INDEX + ADD CONSTRAINT

-- ----- Part 1: version 컬럼 추가 -----
-- 실행 전 확인: SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS
--               WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'version';
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE carts ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- ----- Part 2: unique 제약 이름 명시적 지정 -----
-- 기존 auto-generated 제약명을 명시적 이름으로 교체합니다.
-- 기존 제약명 확인:
--   SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS
--   WHERE TABLE_NAME = 'likes' AND CONSTRAINT_TYPE = 'UNIQUE';
-- 아래 [기존_제약명]을 실제 값으로 교체 후 실행하세요.

-- likes 테이블: (user_id, product_id)
ALTER TABLE likes DROP INDEX [기존_제약명];
ALTER TABLE likes ADD CONSTRAINT uk_likes_user_product UNIQUE (user_id, product_id);

-- coupon_issues 테이블: (coupon_id, user_id)
ALTER TABLE coupon_issues DROP INDEX [기존_제약명];
ALTER TABLE coupon_issues ADD CONSTRAINT uk_coupon_issues_coupon_user UNIQUE (coupon_id, user_id);

-- users 테이블: (login_id)
ALTER TABLE users DROP INDEX [기존_제약명];
ALTER TABLE users ADD CONSTRAINT uk_users_login_id UNIQUE (login_id);

-- carts 테이블: (user_id)
ALTER TABLE carts DROP INDEX [기존_제약명];
ALTER TABLE carts ADD CONSTRAINT uk_carts_user_id UNIQUE (user_id);
