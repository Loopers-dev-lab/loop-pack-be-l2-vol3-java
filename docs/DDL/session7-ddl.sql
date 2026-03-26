-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
-- Session 7: 이벤트 기반 아키텍처 공통 DDL
-- 실행 순서: 이 파일을 먼저 실행한 후 Step 1~3 구현 시작
-- ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

-- ━━ 1. Outbox 테이블 (commerce-api에서 INSERT, Relay가 Kafka로 발행) ━━
CREATE TABLE IF NOT EXISTS outbox_event (
    event_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type VARCHAR(50)  NOT NULL,          -- ORDER, PRODUCT 등
    aggregate_id   VARCHAR(100) NOT NULL,          -- 도메인 ID
    event_type     VARCHAR(100) NOT NULL,          -- ORDER_CREATED, PRODUCT_LIKED 등
    topic          VARCHAR(100) NOT NULL,          -- Kafka 토픽명
    partition_key  VARCHAR(100) NOT NULL,          -- Kafka 파티션 키
    payload        JSON         NOT NULL,          -- 이벤트 데이터
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING → PUBLISHED / FAILED / DEAD
    retry_count    INT          NOT NULL DEFAULT 0,
    last_error     VARCHAR(500) NULL,
    next_retry_at  DATETIME(6)  NULL,
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at   DATETIME(6)  NULL,
    INDEX idx_outbox_pending (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ━━ 2. 쿠폰 상태 확정 전용 Outbox (내부 서비스 호출) ━━
CREATE TABLE IF NOT EXISTS coupon_pending_actions (
    action_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    action_type      VARCHAR(30)  NOT NULL,        -- CONFIRM, RESTORE
    user_coupon_id   BIGINT       NOT NULL,
    order_id         BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING → DONE / FAILED / CANCELLED
    created_at       DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    processed_at     DATETIME(6)  NULL,
    retry_count      INT          NOT NULL DEFAULT 0,
    error_message    VARCHAR(500) NULL,
    INDEX idx_coupon_action_status (status, created_at),
    INDEX idx_coupon_action_user_coupon (user_coupon_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ━━ 3. 멱등 처리 — event_handled (핫 테이블, Consumer 공통) ━━
CREATE TABLE IF NOT EXISTS event_handled (
    event_id   BIGINT PRIMARY KEY,
    handled_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ━━ 4. 처리 이력/감사 — event_log (콜드 테이블, Consumer 공통) ━━
CREATE TABLE IF NOT EXISTS event_log (
    log_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id      BIGINT       NOT NULL,
    event_type    VARCHAR(50)  NOT NULL,
    topic         VARCHAR(100) NOT NULL,
    status        VARCHAR(20)  NOT NULL,           -- SUCCESS, SKIPPED, FAILED
    error_message VARCHAR(500) NULL,
    handled_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_event_log_type_time (event_type, handled_at),
    INDEX idx_event_log_status (status, handled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ━━ 5. 상품 메트릭스 (commerce-streamer에서 upsert) ━━
CREATE TABLE IF NOT EXISTS product_metrics (
    product_id      BIGINT PRIMARY KEY,
    like_count      BIGINT NOT NULL DEFAULT 0,
    like_version    BIGINT NOT NULL DEFAULT 0,     -- 순서 역전 방어 (Consumer 측 단조 증가, 갱신 시 +1)
    like_updated_at DATETIME(6) NULL,              -- 운영/디버깅용
    view_count      BIGINT NOT NULL DEFAULT 0,
    sales_count     BIGINT NOT NULL DEFAULT 0,
    sales_amount    BIGINT NOT NULL DEFAULT 0,
    updated_at      DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ━━ 6. 쿠폰 발급 결과 (Step 3) ━━
CREATE TABLE IF NOT EXISTS coupon_issue_result (
    request_id   VARCHAR(36)  PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    coupon_id    BIGINT       NOT NULL,
    status       VARCHAR(20)  NOT NULL,            -- REQUESTED, ISSUED, REJECTED
    reason       VARCHAR(100) NULL,
    created_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at   DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    INDEX idx_coupon_issue_user (user_id, coupon_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ━━ 7. 기존 coupons 테이블 변경 (선착순 수량 제한) ━━
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS max_quantity INT NULL AFTER expired_at;
ALTER TABLE coupons ADD COLUMN IF NOT EXISTS issued_count INT NOT NULL DEFAULT 0 AFTER max_quantity;
