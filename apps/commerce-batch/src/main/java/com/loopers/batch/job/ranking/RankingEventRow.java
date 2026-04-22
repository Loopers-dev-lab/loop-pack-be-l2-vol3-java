package com.loopers.batch.job.ranking;

import java.time.ZonedDateTime;

/**
 * JdbcCursorItemReader로 읽어오는 ranking_event 한 행의 경량 뷰.
 * 배치 스트리밍 중 불필요한 JPA 영속 오버헤드를 피하기 위해 record 사용.
 */
public record RankingEventRow(
        long id,
        long productId,
        String eventType,
        ZonedDateTime eventTime
) {}
