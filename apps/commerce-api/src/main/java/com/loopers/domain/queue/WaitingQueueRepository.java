package com.loopers.domain.queue;

import java.util.Optional;
import java.util.List;

public interface WaitingQueueRepository {

    boolean addIfAbsent(String eventId, Long userId, long score);

    Optional<Long> findRank(String eventId, Long userId);

    long countWaiting(String eventId);

    List<Long> popOldest(String eventId, long count);
}

