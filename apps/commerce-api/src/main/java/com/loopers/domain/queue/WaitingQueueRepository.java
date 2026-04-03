package com.loopers.domain.queue;

import java.util.List;

public interface WaitingQueueRepository {

    // ZADD (NX 없음) — 재진입 시 score 갱신으로 맨 뒤로 이동. true: 신규, false: 재진입
    boolean enqueue(Long userId, double score);

    // ZRANK — 0-based rank, 큐에 없으면 null
    Long getPosition(Long userId);

    // ZCARD — 전체 대기 인원
    long getTotalCount();

    // ZRANGE — 큐에서 제거 없이 상위 N명 조회 (스케줄러용)
    List<Long> peekBatch(int batchSize);

    // ZREM — 폴링 API에서 토큰 확인 후 큐에서 제거
    void remove(Long userId);
}
