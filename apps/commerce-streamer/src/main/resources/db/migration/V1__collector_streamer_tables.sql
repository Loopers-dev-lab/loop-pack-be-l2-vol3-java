-- commerce-streamer collector 전용 테이블 (운영은 Flyway로만 변경; JPA 엔티티와 동일 스키마 유지)
CREATE TABLE IF NOT EXISTS event_handled (
  event_id VARCHAR(64) NOT NULL PRIMARY KEY,
  topic VARCHAR(200) NOT NULL,
  partition_no INT NULL,
  offset_no BIGINT NULL,
  handled_at DATETIME(6) NOT NULL,
  KEY idx_event_handled_handled_at (handled_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS product_metrics (
  product_id BIGINT NOT NULL PRIMARY KEY,
  like_count BIGINT NOT NULL,
  view_count BIGINT NOT NULL,
  sold_quantity BIGINT NOT NULL,
  last_event_occurred_at DATETIME(6) NULL,
  updated_at DATETIME(6) NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
