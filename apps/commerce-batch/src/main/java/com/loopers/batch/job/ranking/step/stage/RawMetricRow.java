package com.loopers.batch.job.ranking.step.stage;

import java.time.LocalDateTime;

/**
 * Cursor 로 한 줄씩 흘러오는 원시 메트릭 row.
 * (product_id ASC, bucket_time ASC) 순서로 전달되어야 한다.
 */
public record RawMetricRow(Long productId, LocalDateTime bucketTime, long count) {
}
