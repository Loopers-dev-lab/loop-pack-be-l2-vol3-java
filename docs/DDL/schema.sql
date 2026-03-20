-- =============================================================================
-- Loopers 감성 이커머스 DB Schema
-- Database : loopers (MySQL 8.0)
-- Charset  : utf8mb4 / utf8mb4_unicode_ci
-- Created  : 2026-03-11
-- Notes    :
--   · ddl-auto: create  (local / test — Hibernate 자동 생성)
--   · ddl-auto: none    (dev / qa / prd — 이 스크립트로 수동 적용)
--   · PK 전략: bigint AUTO_INCREMENT (BaseStringIdEntity 서브클래스)
--   · 소프트 삭제: del_yn CHAR(1) + deleted_at (BaseStringIdEntity 상속 도메인)
--   · FK 제약: 설계 의도에 따라 주석 처리 (애플리케이션 레벨 정합성 유지)
--             필요 시 주석 해제하여 적용 가능
-- =============================================================================

CREATE DATABASE IF NOT EXISTS loopers
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE loopers;

-- =============================================================================
-- 1. users
-- =============================================================================
CREATE TABLE IF NOT EXISTS users
(
    user_id    BIGINT       NOT NULL AUTO_INCREMENT COMMENT '사용자 PK',
    login_id   VARCHAR(50)  NOT NULL                COMMENT '로그인 ID (영숫자, 인증용)',
    password   VARCHAR(100) NOT NULL                COMMENT 'BCrypt 인코딩된 비밀번호',
    user_name  VARCHAR(50)  NOT NULL                COMMENT '사용자 이름',
    birthday   VARCHAR(8)   NULL                    COMMENT '생년월일 (YYYYMMDD)',
    email      VARCHAR(100) NULL                    COMMENT '이메일',
    address    VARCHAR(255) NULL                    COMMENT '주소',
    del_yn     CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '소프트 삭제 여부 (Y/N)',
    deleted_at DATETIME     NULL                    COMMENT '삭제 일시 (del_yn=Y 시 설정)',
    created_at DATETIME     NOT NULL                COMMENT '생성 일시',
    updated_at DATETIME     NOT NULL                COMMENT '수정 일시',
    PRIMARY KEY (user_id),
    UNIQUE KEY uq_users_login_id (login_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '사용자';


-- =============================================================================
-- 2. brands
-- =============================================================================
CREATE TABLE IF NOT EXISTS brands
(
    brand_id       BIGINT       NOT NULL AUTO_INCREMENT COMMENT '브랜드 PK',
    brand_name     TEXT         NOT NULL                COMMENT '브랜드명',
    description    TEXT         NULL                    COMMENT '브랜드 설명',
    address        VARCHAR(255) NULL                    COMMENT '브랜드 주소',
    display_status VARCHAR(20)  NOT NULL                COMMENT '노출 상태 (ACTIVE/HIDDEN)',
    attach_file    VARCHAR(500) NULL                    COMMENT '첨부 파일 경로',
    del_yn         CHAR(1)      NOT NULL DEFAULT 'N'    COMMENT '소프트 삭제 여부 (Y/N)',
    deleted_at     DATETIME     NULL                    COMMENT '삭제 일시',
    created_at     DATETIME     NOT NULL                COMMENT '생성 일시',
    updated_at     DATETIME     NOT NULL                COMMENT '수정 일시',
    PRIMARY KEY (brand_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '브랜드';


-- =============================================================================
-- 3. products
-- =============================================================================
CREATE TABLE IF NOT EXISTS products
(
    product_id     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '상품 PK',
    brand_id       BIGINT        NOT NULL                COMMENT '소속 브랜드 FK',
    product_name   VARCHAR(255)  NOT NULL                COMMENT '상품명',
    description    TEXT          NULL                    COMMENT '상품 설명',
    price          DECIMAL(12,2) NOT NULL                COMMENT '판매 가격',
    category       VARCHAR(100)  NULL                    COMMENT '카테고리',
    color          VARCHAR(50)   NULL                    COMMENT '색상',
    size           VARCHAR(50)   NULL                    COMMENT '사이즈',
    option         VARCHAR(255)  NULL                    COMMENT '옵션 정보',
    image_url      VARCHAR(500)  NULL                    COMMENT '대표 이미지 URL',
    attach_file    VARCHAR(500)  NULL                    COMMENT '첨부 파일 경로',
    display_status VARCHAR(20)   NOT NULL                COMMENT '노출 상태 (ACTIVE/HIDDEN)',
    sale_status    VARCHAR(20)   NOT NULL                COMMENT '판매 상태 (ON_SALE/TEMP_SOLD_OUT/STOPPED)',
    revision_seq   BIGINT        NOT NULL DEFAULT 0      COMMENT '현재 변경 이력 순번 (최신 ProductRevision 포인터)',
    like_count     BIGINT        NOT NULL DEFAULT 0      COMMENT '좋아요 수 (비정규화)',
    del_yn         CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '소프트 삭제 여부 (Y/N)',
    deleted_at     DATETIME      NULL                    COMMENT '삭제 일시',
    created_at     DATETIME      NOT NULL                COMMENT '생성 일시',
    updated_at     DATETIME      NOT NULL                COMMENT '수정 일시',
    PRIMARY KEY (product_id)
    -- , CONSTRAINT fk_products_brand_id FOREIGN KEY (brand_id) REFERENCES brands (brand_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '상품';

CREATE INDEX idx_products_like_count ON products (like_count DESC);

-- 기존 데이터 마이그레이션 (like_count 초기화)
UPDATE products p
SET p.like_count = (SELECT COUNT(*) FROM likes l WHERE l.product_id = p.product_id);


-- =============================================================================
-- 4. product_stocks  (products 와 1:1, Hot Row 격리)
-- =============================================================================
CREATE TABLE IF NOT EXISTS product_stocks
(
    product_id BIGINT   NOT NULL       COMMENT '상품 FK이자 PK (products 와 1:1)',
    on_hand    INT      NOT NULL       COMMENT '총 재고 수량',
    reserved   INT      NOT NULL DEFAULT 0 COMMENT '예약(hold) 재고 수량',
    created_at DATETIME NOT NULL       COMMENT '생성 일시',
    updated_at DATETIME NOT NULL       COMMENT '수정 일시',
    PRIMARY KEY (product_id)
    -- , CONSTRAINT fk_product_stocks_product_id FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '상품 재고 (CAS UPDATE 전용, Hot Row 격리)';


-- =============================================================================
-- 5. product_revisions  (복합 PK: product_id + revision_seq)
-- =============================================================================
CREATE TABLE IF NOT EXISTS product_revisions
(
    product_id      BIGINT       NOT NULL COMMENT '상품 FK (복합 PK)',
    revision_seq    BIGINT       NOT NULL COMMENT '변경 순번 (복합 PK)',
    action          VARCHAR(30)  NOT NULL COMMENT '변경 유형 (CREATE/UPDATE/HIDE/SALE_STATUS_CHANGE/DELETE/RESTORE)',
    changed_by      VARCHAR(100) NULL     COMMENT '변경 수행자 (admin ID 또는 system)',
    change_reason   VARCHAR(500) NULL     COMMENT '변경 사유',
    before_snapshot JSON         NULL     COMMENT '변경 전 상품 전체 상태 스냅샷',
    after_snapshot  JSON         NULL     COMMENT '변경 후 상품 전체 상태 스냅샷',
    created_at      DATETIME     NOT NULL COMMENT '이력 생성 일시',
    PRIMARY KEY (product_id, revision_seq)
    -- , CONSTRAINT fk_product_revisions_product_id FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '상품 변경 이력 (before/after JSON 스냅샷)';


-- =============================================================================
-- 6. likes  (복합 PK: user_id + product_id)
-- =============================================================================
CREATE TABLE IF NOT EXISTS likes
(
    user_id    BIGINT   NOT NULL COMMENT '사용자 FK (복합 PK)',
    product_id BIGINT   NOT NULL COMMENT '상품 FK (복합 PK)',
    created_at DATETIME NOT NULL COMMENT '좋아요 등록 일시',
    PRIMARY KEY (user_id, product_id)
    -- , CONSTRAINT fk_likes_user_id    FOREIGN KEY (user_id)    REFERENCES users    (user_id)
    -- , CONSTRAINT fk_likes_product_id FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '상품 좋아요 (사용자당 상품별 1건, 멱등)';


-- =============================================================================
-- 7. cart_items  (복합 PK: user_id + product_id)
-- =============================================================================
CREATE TABLE IF NOT EXISTS cart_items
(
    user_id    BIGINT   NOT NULL COMMENT '사용자 FK (복합 PK)',
    product_id BIGINT   NOT NULL COMMENT '상품 FK (복합 PK)',
    quantity   INT      NOT NULL COMMENT '수량 (1 이상)',
    created_at DATETIME NOT NULL COMMENT '장바구니 등록 일시',
    updated_at DATETIME NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (user_id, product_id)
    -- , CONSTRAINT fk_cart_items_user_id    FOREIGN KEY (user_id)    REFERENCES users    (user_id)
    -- , CONSTRAINT fk_cart_items_product_id FOREIGN KEY (product_id) REFERENCES products (product_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '장바구니 항목 (사용자당 상품별 1건, 수량 병합)';


-- =============================================================================
-- 8. orders
-- =============================================================================
CREATE TABLE IF NOT EXISTS orders
(
    order_id     BIGINT        NOT NULL AUTO_INCREMENT COMMENT '주문 PK',
    user_id      BIGINT        NOT NULL                COMMENT '주문자 FK',
    order_type   VARCHAR(20)   NOT NULL                COMMENT '주문 유형 (DIRECT/CART)',
    status       VARCHAR(20)   NOT NULL                COMMENT '주문 상태 (PENDING_PAYMENT/CANCELLED/EXPIRED)',
    total_amount DECIMAL(12,2) NOT NULL                COMMENT '주문 총액',
    expires_at   DATETIME      NOT NULL                COMMENT '결제 만료 일시 (생성 후 15분)',
    paid_at      DATETIME      NULL                    COMMENT '결제 완료 일시 (Phase2)',
    del_yn       CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '소프트 삭제 여부 (Y/N)',
    deleted_at   DATETIME      NULL                    COMMENT '삭제 일시',
    created_at   DATETIME      NOT NULL                COMMENT '생성 일시',
    updated_at   DATETIME      NOT NULL                COMMENT '수정 일시',
    PRIMARY KEY (order_id)
    -- , CONSTRAINT fk_orders_user_id FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '주문 (상태 머신: PENDING_PAYMENT → CANCELLED / EXPIRED)';


-- =============================================================================
-- 9. order_items  (복합 PK: order_id + order_item_seq)
-- =============================================================================
CREATE TABLE IF NOT EXISTS order_items
(
    order_id              BIGINT        NOT NULL COMMENT '주문 FK (복합 PK)',
    order_item_seq        INT           NOT NULL COMMENT '주문 내 항목 순번 (복합 PK)',
    user_id               BIGINT        NOT NULL COMMENT '주문자 FK',
    product_id            BIGINT        NOT NULL COMMENT '상품 FK',
    quantity              INT           NOT NULL COMMENT '주문 수량',
    snapshot_product_name VARCHAR(255)  NULL     COMMENT '주문 시점 상품명 스냅샷',
    snapshot_unit_price   DECIMAL(12,2) NULL     COMMENT '주문 시점 단가 스냅샷',
    snapshot_brand_id     VARCHAR(36)   NULL     COMMENT '주문 시점 브랜드 ID 스냅샷',
    snapshot_brand_name   VARCHAR(255)  NULL     COMMENT '주문 시점 브랜드명 스냅샷',
    snapshot_image_url    VARCHAR(500)  NULL     COMMENT '주문 시점 이미지 URL 스냅샷',
    del_yn                CHAR(1)       NOT NULL DEFAULT 'N' COMMENT '소프트 삭제 여부 (Y/N)',
    deleted_at            DATETIME      NULL     COMMENT '삭제 일시',
    created_at            DATETIME      NOT NULL COMMENT '생성 일시',
    updated_at            DATETIME      NOT NULL COMMENT '수정 일시',
    PRIMARY KEY (order_id, order_item_seq)
    -- , CONSTRAINT fk_order_items_order_id FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '주문 항목 (주문 시점 상품 정보 스냅샷 포함)';


-- =============================================================================
-- 10. order_cart_restore  (PK = order_id, 멱등 보장)
-- =============================================================================
-- ALTER TABLE order_items
--     ADD COLUMN original_amount DECIMAL(12, 2) NULL COMMENT '할인 전 금액' AFTER snapshot_unit_price,
--     ADD COLUMN discount_amount DECIMAL(12, 2) NULL COMMENT '할인 금액' AFTER original_amount,
--     ADD COLUMN final_amount    DECIMAL(12, 2) NULL COMMENT '최종 결제 금액' AFTER discount_amount;
--
-- =============================================================================
-- 11. coupons  (쿠폰 템플릿)
-- =============================================================================
CREATE TABLE IF NOT EXISTS coupons
(
    coupon_id        BIGINT         NOT NULL AUTO_INCREMENT COMMENT '쿠폰 템플릿 PK',
    name             VARCHAR(255)   NOT NULL               COMMENT '쿠폰명',
    type             VARCHAR(10)    NOT NULL               COMMENT '할인 유형 (FIXED|RATE)',
    value            DECIMAL(10, 2) NOT NULL               COMMENT '할인값 (원 또는 %)',
    min_order_amount DECIMAL(12, 2) NULL                   COMMENT '최소 주문 금액 조건',
    expired_at       DATETIME       NOT NULL               COMMENT '쿠폰 만료 일시',
    del_yn           CHAR(1)        NOT NULL DEFAULT 'N'   COMMENT '소프트 삭제 여부 (Y/N)',
    deleted_at       DATETIME       NULL                   COMMENT '삭제 일시',
    created_at       DATETIME       NOT NULL               COMMENT '생성 일시',
    updated_at       DATETIME       NOT NULL               COMMENT '수정 일시',
    PRIMARY KEY (coupon_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '쿠폰 템플릿';


-- =============================================================================
-- 12. user_coupons  (사용자 발급 쿠폰)
-- =============================================================================
CREATE TABLE IF NOT EXISTS user_coupons
(
    user_coupon_id BIGINT      NOT NULL AUTO_INCREMENT COMMENT '발급 쿠폰 PK',
    user_id        BIGINT      NOT NULL               COMMENT '사용자 ID',
    coupon_id      BIGINT      NOT NULL               COMMENT '쿠폰 템플릿 ID',
    status         VARCHAR(20) NOT NULL               COMMENT '상태 (AVAILABLE|USED|EXPIRED)',
    issued_at      DATETIME    NOT NULL               COMMENT '발급 일시',
    used_at        DATETIME    NULL                   COMMENT '사용 일시',
    order_id       BIGINT      NULL                   COMMENT '사용된 주문 ID',
    PRIMARY KEY (user_coupon_id),
    UNIQUE KEY uq_user_coupons (user_id, coupon_id),
    INDEX idx_user_coupons_user_id (user_id),
    INDEX idx_user_coupons_coupon_id (coupon_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '사용자 발급 쿠폰 (사용자당 쿠폰별 1건, 상태 관리)';


-- =============================================================================
-- 12(cont). order_cart_restore  (PK = order_id, 멱등 보장)
-- =============================================================================
CREATE TABLE IF NOT EXISTS order_cart_restore
(
    order_id       BIGINT      NOT NULL COMMENT '주문 PK이자 FK — 주문당 1건만 복원 (멱등키)',
    user_id        BIGINT      NOT NULL COMMENT '복원 대상 사용자',
    reason         VARCHAR(30) NOT NULL COMMENT '복원 사유 (USER_CANCELLED/EXPIRED/PAYMENT_FAILED/PG_CANCELLED)',
    trigger_source VARCHAR(30) NOT NULL COMMENT '복원 트리거 출처 (CANCEL_API/EXPIRE_JOB/PG_WEBHOOK/MANUAL)',
    restored_at    DATETIME    NOT NULL COMMENT '복원 처리 완료 일시',
    PRIMARY KEY (order_id)
    -- , CONSTRAINT fk_order_cart_restore_order_id FOREIGN KEY (order_id) REFERENCES orders (order_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '장바구니 복원 이력 (DIRECT 주문 취소/만료 시 1회 복원 멱등 보장)';


-- =============================================================================
-- 13. payment  (주문:결제 = 1:N, 결제 시도 이력)
-- =============================================================================
CREATE TABLE IF NOT EXISTS payment
(
    payment_id      BIGINT        NOT NULL AUTO_INCREMENT COMMENT '결제 PK',
    order_id        BIGINT        NOT NULL                COMMENT '주문 FK',
    user_id         BIGINT        NOT NULL                COMMENT '사용자 FK',
    transaction_key VARCHAR(30)   NULL                    COMMENT 'PG 트랜잭션 식별자 (PG 응답 시 수신)',
    card_type       VARCHAR(20)   NOT NULL                COMMENT '카드 종류 (SAMSUNG/KB/HYUNDAI)',
    card_no_masked  VARCHAR(30)   NOT NULL                COMMENT '마스킹된 카드번호',
    amount          DECIMAL(12,2) NOT NULL                COMMENT '결제 금액',
    status          VARCHAR(20)   NOT NULL DEFAULT 'REQUESTED' COMMENT '결제 상태 (REQUESTED/SUCCESS/FAILED/CANCELLED)',
    failure_reason  VARCHAR(500)  NULL                    COMMENT '실패 사유',
    paid_at         DATETIME      NULL                    COMMENT '결제 완료 일시',
    del_yn          CHAR(1)       NOT NULL DEFAULT 'N'    COMMENT '소프트 삭제 여부 (Y/N)',
    deleted_at      DATETIME      NULL                    COMMENT '삭제 일시',
    created_at      DATETIME      NOT NULL                COMMENT '생성 일시',
    updated_at      DATETIME      NOT NULL                COMMENT '수정 일시',
    PRIMARY KEY (payment_id),
    INDEX idx_payment_order_id (order_id),
    INDEX idx_payment_transaction_key (transaction_key),
    INDEX idx_payment_status_created (status, created_at),
    INDEX idx_payment_user_order (user_id, order_id)
    -- , CONSTRAINT fk_payment_order_id FOREIGN KEY (order_id) REFERENCES orders (order_id)
    -- , CONSTRAINT fk_payment_user_id  FOREIGN KEY (user_id)  REFERENCES users  (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '결제 (주문당 N건 시도 가능, CAS 상태 전이)';


-- =============================================================================
-- 14. payment_compensation  (stock commit 실패 등 보정 대상)
-- =============================================================================
CREATE TABLE IF NOT EXISTS payment_compensation
(
    compensation_id BIGINT       NOT NULL AUTO_INCREMENT COMMENT '보정 PK',
    payment_id      BIGINT       NOT NULL                COMMENT '결제 FK',
    order_id        BIGINT       NOT NULL                COMMENT '주문 FK',
    failure_reason  TEXT         NULL                    COMMENT '실패 사유',
    retry_count     INT          NOT NULL DEFAULT 0      COMMENT '재시도 횟수',
    max_retries     INT          NOT NULL DEFAULT 3      COMMENT '최대 재시도 횟수',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '보정 상태 (PENDING/RESOLVED/MANUAL_REQUIRED)',
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일시',
    resolved_at     DATETIME(6)  NULL                    COMMENT '해결 일시',
    PRIMARY KEY (compensation_id),
    INDEX idx_payment_compensation_status (status),
    INDEX idx_payment_compensation_payment_id (payment_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '결제 보정 (stock commit 실패 등 후속 처리 실패 시 기록)';


