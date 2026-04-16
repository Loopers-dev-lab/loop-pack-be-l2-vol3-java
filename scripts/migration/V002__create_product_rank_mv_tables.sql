-- ============================================================================
-- V002: 주간/월간 상품 랭킹 Materialized View 테이블 생성
-- ============================================================================
--
-- 설계 의사결정 기록:
--
-- 1. period_start_date + period_end_date vs period_type ENUM
--    - 선택: period_start_date + period_end_date 별도 컬럼
--    - 이유:
--      * 유연한 기간 정의 가능 (월별로 28~31일 가변, 윤년 등)
--      * 커스텀 기간 집계 확장 가능 (분기별, 이벤트 기간 등)
--      * WHERE 조건 직관적 (period_start_date = '2025-01-06')
--    - 트레이드오프:
--      * 저장 공간 약간 증가 (DATE 2개 vs ENUM 1개)
--      * period_type은 테이블 분리로 해결 (weekly/monthly 테이블 분리)
--
-- 2. rank_number를 저장하는 이유
--    - 조회 시 ORDER BY + LIMIT 연산 제거 → 읽기 최적화
--    - 배치 Job에서 한 번만 정렬하고, API는 단순 조회만 수행
--    - 순위 변동 이력 추적 가능
--
-- 3. TOP 100만 저장하는 이유
--    - 저장 공간 절약 (주간: 52주 × 100건 = 5,200건/년)
--    - 실제 서비스에서 100위 밖은 노출하지 않음
--    - 필요 시 TOP N 확장 용이 (테이블 구조 변경 불필요)
--
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 주간 TOP 100 랭킹 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mv_product_rank_weekly (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    product_id          BIGINT          NOT NULL COMMENT '상품 ID',
    rank_number         INT             NOT NULL COMMENT '해당 주간 순위 (1~100)',
    total_score         DECIMAL(15, 4)  NOT NULL DEFAULT 0 COMMENT '주간 합산 점수',
    total_view_count    BIGINT          NOT NULL DEFAULT 0 COMMENT '주간 조회수 합계',
    total_like_count    BIGINT          NOT NULL DEFAULT 0 COMMENT '주간 좋아요수 합계',
    total_order_count   BIGINT          NOT NULL DEFAULT 0 COMMENT '주간 주문수 합계',
    period_start_date   DATE            NOT NULL COMMENT '집계 시작일 (월요일)',
    period_end_date     DATE            NOT NULL COMMENT '집계 종료일 (일요일)',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성 시각',

    PRIMARY KEY (id),

    -- 특정 주간의 랭킹 조회 최적화 (가장 빈번한 쿼리 패턴)
    -- SELECT * FROM mv_product_rank_weekly WHERE period_start_date = ? ORDER BY rank_number
    INDEX idx_weekly_period_rank (period_start_date, rank_number),

    -- 특정 상품의 주간 랭킹 이력 조회
    -- SELECT * FROM mv_product_rank_weekly WHERE product_id = ? ORDER BY period_start_date DESC
    INDEX idx_weekly_product (product_id),

    -- 동일 주간에 동일 상품 중복 방지
    UNIQUE INDEX uk_weekly_period_product (period_start_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='주간 상품 랭킹 TOP 100 (Materialized View)';


-- ----------------------------------------------------------------------------
-- 월간 TOP 100 랭킹 테이블
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS mv_product_rank_monthly (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    product_id          BIGINT          NOT NULL COMMENT '상품 ID',
    rank_number         INT             NOT NULL COMMENT '해당 월간 순위 (1~100)',
    total_score         DECIMAL(15, 4)  NOT NULL DEFAULT 0 COMMENT '월간 합산 점수',
    total_view_count    BIGINT          NOT NULL DEFAULT 0 COMMENT '월간 조회수 합계',
    total_like_count    BIGINT          NOT NULL DEFAULT 0 COMMENT '월간 좋아요수 합계',
    total_order_count   BIGINT          NOT NULL DEFAULT 0 COMMENT '월간 주문수 합계',
    period_start_date   DATE            NOT NULL COMMENT '집계 시작일 (매월 1일)',
    period_end_date     DATE            NOT NULL COMMENT '집계 종료일 (매월 말일)',
    created_at          DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '레코드 생성 시각',

    PRIMARY KEY (id),

    -- 특정 월간의 랭킹 조회 최적화
    INDEX idx_monthly_period_rank (period_start_date, rank_number),

    -- 특정 상품의 월간 랭킹 이력 조회
    INDEX idx_monthly_product (product_id),

    -- 동일 월간에 동일 상품 중복 방지
    UNIQUE INDEX uk_monthly_period_product (period_start_date, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='월간 상품 랭킹 TOP 100 (Materialized View)';