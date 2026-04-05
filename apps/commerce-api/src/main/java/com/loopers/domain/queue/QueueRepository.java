package com.loopers.domain.queue;

import java.util.List;

public interface QueueRepository {

    // 대기열 (Wait Queue)
    boolean enterWaitQueue(Long userId, double score);

    Long getWaitPosition(Long userId);

    Long getWaitTotalCount();

    // 활성화 (Wait → Token 발급)
    List<Long> popFromWaitQueue(long count);

    void issueToken(Long userId, String token, long ttlSeconds);

    // 토큰 검증 / 삭제
    boolean hasActiveToken(Long userId);

    String getToken(Long userId);

    void removeToken(Long userId);

    // 활성 토큰 카운터
    long getActiveTokenCount();

    void incrementActiveTokenCount(long count, long ttlSeconds);

    void decrementActiveTokenCount();
}
