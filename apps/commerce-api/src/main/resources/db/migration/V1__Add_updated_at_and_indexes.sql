-- Outbox 개선: updated_at 컬럼 및 인덱스 추가

-- updated_at 컬럼 추가
ALTER TABLE outbox_event
ADD COLUMN updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

-- 기존 데이터 updated_at 초기화 (created_at과 동일)
UPDATE outbox_event
SET updated_at = created_at
WHERE updated_at IS NULL;

-- 인덱스 최적화
-- PROCESSING 복구용 (updated_at 기준)
CREATE INDEX idx_outbox_processing_updated ON outbox_event (updated_at)
WHERE status = 'PROCESSING';

-- PENDING 조회 최적화 (Partial Index)
CREATE INDEX idx_outbox_pending_created ON outbox_event (created_at)
WHERE status = 'PENDING';

-- PROCESSING 조회 최적화 (Partial Index)
CREATE INDEX idx_outbox_processing_created ON outbox_event (created_at)
WHERE status = 'PROCESSING';

-- 기존 복합 인덱스는 유지 (status, created_at)
-- 기존 복합 인덱스는 유지 (status, updated_at)
