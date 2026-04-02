package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface QueueRepository {

    void enter(long userId, double score);

    boolean isInWaiting(long userId);

    Optional<Long> getRank(long userId);

    Optional<String> findToken(long userId);

    List<Long> moveToActive(int count, long ttlSeconds, List<String> uuids);

    void removeToken(long userId);
}
