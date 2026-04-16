-- ============================================================================
-- S2 Atomic Swap 전환 마이그레이션 (2026-04-15)
-- Target: 기존 프로덕션 환경 (기존 mv_product_rank_weekly/_monthly 데이터 보존)
-- Precondition: commerce-api / commerce-batch 신 버전 배포 **이전** 실행
-- ============================================================================

-- 1. MV 테이블에 version 컬럼 추가 (기존 행은 DEFAULT 1로 채움)
ALTER TABLE mv_product_rank_weekly
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER period_key;

ALTER TABLE mv_product_rank_monthly
    ADD COLUMN version BIGINT NOT NULL DEFAULT 1 AFTER period_key;

-- 2. PK 재정의: (period_key, rank_no) -> (period_key, version, rank_no)
ALTER TABLE mv_product_rank_weekly
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (period_key, version, rank_no);

ALTER TABLE mv_product_rank_monthly
    DROP PRIMARY KEY,
    ADD PRIMARY KEY (period_key, version, rank_no);

-- 3. Unique key 재정의: version 포함
ALTER TABLE mv_product_rank_weekly
    DROP INDEX uk_period_product,
    ADD UNIQUE KEY uk_period_version_product (period_key, version, ref_product_id);

ALTER TABLE mv_product_rank_monthly
    DROP INDEX uk_period_product_monthly,
    ADD UNIQUE KEY uk_period_version_product_monthly (period_key, version, ref_product_id);

-- 4. Publication 테이블 생성
CREATE TABLE IF NOT EXISTS mv_product_rank_publication (
    period_type       VARCHAR(20)  NOT NULL,
    period_key        VARCHAR(8)   NOT NULL,
    published_version BIGINT       NOT NULL DEFAULT 0,
    next_version      BIGINT       NOT NULL DEFAULT 0,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (period_type, period_key)
);

-- 5. 기존 periodKey들에 대해 Publication 초기 row seed (기존 행은 version=1이므로 published=1)
INSERT INTO mv_product_rank_publication (period_type, period_key, published_version, next_version, updated_at)
SELECT 'WEEKLY', period_key, 1, 1, NOW(6)
FROM (SELECT DISTINCT period_key FROM mv_product_rank_weekly) w
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

INSERT INTO mv_product_rank_publication (period_type, period_key, published_version, next_version, updated_at)
SELECT 'MONTHLY', period_key, 1, 1, NOW(6)
FROM (SELECT DISTINCT period_key FROM mv_product_rank_monthly) m
ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at);

-- 6. 검증 쿼리 (실행 후 결과 확인)
-- SELECT 'weekly rows', COUNT(*) FROM mv_product_rank_weekly;
-- SELECT 'monthly rows', COUNT(*) FROM mv_product_rank_monthly;
-- SELECT 'publication rows', COUNT(*) FROM mv_product_rank_publication;
-- SELECT period_type, COUNT(*) FROM mv_product_rank_publication GROUP BY period_type;
-- SELECT * FROM mv_product_rank_publication ORDER BY period_type, period_key LIMIT 10;

-- ============================================================================
-- 롤백 SQL (신 배포 실패 시 구버전으로 되돌릴 때 실행)
-- 주의: version 컬럼을 보존해도 구 reader는 `SELECT FROM mv_product_rank_weekly WHERE period_key=?`
-- 형태라 무해. PK는 구조 복원 필요 (version 포함된 상태로 두면 rank_no 중복 가능).
-- ============================================================================
-- ALTER TABLE mv_product_rank_weekly
--     DROP PRIMARY KEY,
--     ADD PRIMARY KEY (period_key, rank_no);
-- ALTER TABLE mv_product_rank_monthly
--     DROP PRIMARY KEY,
--     ADD PRIMARY KEY (period_key, rank_no);
-- -- Publication 테이블은 보존 (다음 재시도용). 필요 시 DROP TABLE.
