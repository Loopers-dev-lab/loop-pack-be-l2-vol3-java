package com.loopers.domain.queue;

import java.util.Set;

public interface QueueRepository {

    boolean enter(Long userId, double score);

    Long getPosition(Long userId);

    long getTotalSize();

    Set<String> pollBatch(int count);
}
