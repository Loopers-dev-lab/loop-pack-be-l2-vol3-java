package com.loopers.domain.queue.repository;

import java.util.Set;

public interface QueueRepository {

    Long generateSequence();

    boolean addToQueue(Long memberId, long score);

    Long getRank(Long memberId);

    Long getTotalWaiting();

    Double getScore(Long memberId);

    void removeFromQueue(Long memberId);

    Set<String> getTopMembers(int count);

    Set<String> getMembers(long start, long end);

    QueueEntryResult enterQueue(Long memberId);

    record QueueEntryResult(long rank, long total, boolean isNew) {}

    Long getActiveTokenCount();

    void incrementActiveTokens();

    void decrementActiveTokens();

    void setToken(Long memberId, String token, long ttlSeconds);

    String getToken(Long memberId);

    void deleteToken(Long memberId);

    boolean refreshTokenTtl(Long memberId, long ttlSeconds);
}
