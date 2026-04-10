package com.loopers.domain.queue;

import java.util.Optional;

public interface QueueRepository {
    long enter(String userId, long score);
    Optional<Long> findPosition(String userId);
    long getTotalCount();
    void savePresence(String userId);
    void refreshPresence(String userId);
}