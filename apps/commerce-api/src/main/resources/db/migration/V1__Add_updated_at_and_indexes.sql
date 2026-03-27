-- Outbox 개선: updated_at 컬럼 및 인덱스 추가

-- updated_at 컬럼 추가
ALTER TABLE outbox_event
ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- 기존 데이터 updated_at 초기화 (created_at과 동일)
UPDATE outbox_event
SET updated_at = created_at
WHERE updated_at IS NULL;

-- 인덱스 최적화 (MySQL 호환 — Partial Index는 PostgreSQL 전용이므로 복합 인덱스로 대체)
-- status가 선두 컬럼이면 PENDING/PROCESSING 조회 모두 커버
CREATE INDEX IF NOT EXISTS idx_outbox_status_updated ON outbox_event (status, updated_at);
CREATE INDEX IF NOT EXISTS idx_outbox_status_created ON outbox_event (status, created_at);

-- 참고: PostgreSQL이면 WHERE 조건부 Partial Index가 더 효율적이지만
-- 이 프로젝트는 MySQL 사용이므로 복합 인덱스로 대체.
-- JPA @Table 어노테이션의 인덱스와 중복될 수 있으나, Flyway 도입 시 정리 예정.
