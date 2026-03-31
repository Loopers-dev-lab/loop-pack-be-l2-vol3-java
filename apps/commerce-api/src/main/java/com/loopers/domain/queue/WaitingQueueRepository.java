package com.loopers.domain.queue;

import java.util.Optional;

public interface WaitingQueueRepository {

    boolean addIfAbsent(String eventId, Long userId, long score);

    Optional<Long> findRank(String eventId, Long userId);

    long countWaiting(String eventId);
}

