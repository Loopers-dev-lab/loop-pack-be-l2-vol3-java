-- ============================================================================
-- V003: 일간 상품 메트릭 테이블 생성
-- ============================================================================
--
-- 목적:
-- - 일별 상품 메트릭 스냅샷 저장
-- - 주간/월간 랭킹 집계 배치 Job의 소스 테이블
-- - 기존 product_metrics (누적 테이블)와 별도로 일간 단위 데이터 관리
--
-- 데이터 적재 방식:
-- - 매일 자정 배치 Job이 Redis ZSET (ranking:all:{yyyyMMdd})에서 데이터를 읽어 적재
-- - 또는 이벤트 발생 시 실시간으로 upsert
--
-- ============================================================================

CREATE TABLE IF NOT EXISTS product_metrics_daily (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    product_id          BIGINT          NOT NULL COMMENT '상품 ID',
    metric_date         DATE            NOT NULL COMMENT '메트릭 날짜',
    view_count          BIGINT          NOT NULL DEFAULT 0 COMMENT '해당일 조회수',
    like_count          BIGINT          NOT NULL DEFAULT 0 COMMENT '해당일 좋아요수',
    order_count         BIGINT          NOT NULL DEFAULT 0 COMMENT '해당일 주문수',
    score               DECIMAL(15, 4)  NOT NULL DEFAULT 0 COMMENT '해당일 랭킹 점수 (view*0.1 + like*0.2 + order*0.7)',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성 시각',
    updated_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '레코드 수정 시각',

    PRIMARY KEY (id),

    -- 특정 날짜 범위의 메트릭 조회 (주간/월간 집계용)
    -- SELECT ... FROM product_metrics_daily WHERE metric_date BETWEEN ? AND ? GROUP BY product_id
    INDEX idx_daily_metric_date (metric_date),

    -- 특정 상품의 일별 메트릭 이력 조회
    INDEX idx_daily_product (product_id),

    -- 동일 날짜에 동일 상품 중복 방지
    UNIQUE INDEX uk_daily_product_date (product_id, metric_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='일간 상품 메트릭 (주간/월간 랭킹 집계용)';