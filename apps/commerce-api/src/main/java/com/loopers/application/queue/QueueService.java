package com.loopers.application.queue;

import java.util.List;

public interface QueueService {
    long enter(Long userId);
    long getRank(Long userId);
    long getSize();
    List<Long> peekBatch(int count);
    void remove(Long userId);
}
