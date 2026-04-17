package com.loopers.domain.ranking;

/**
 * dirty=true인 (productId, metricsHour) 쌍.
 *
 * SyncScheduler가 일간/시간별 랭킹 ZSET을 갱신할 대상을 식별하는 데 사용한다.
 */
public record DirtyEntry(Long productId, int metricsHour) {
}
