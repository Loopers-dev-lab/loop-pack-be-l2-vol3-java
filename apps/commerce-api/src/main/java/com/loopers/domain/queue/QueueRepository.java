package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

public interface QueueRepository {

    boolean add(String eventId, Long userId);

    Optional<Long> getPosition(String eventId, Long userId);

    long getTotalCount(String eventId);

    List<Long> popFront(String eventId, int count);
}
