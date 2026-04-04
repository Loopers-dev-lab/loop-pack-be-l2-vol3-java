package com.loopers.domain.queue;

import java.util.List;

public interface WaitingQueueRepository {

    boolean add(Long userId, double score);

    Long rank(Long userId);

    long size();

    boolean addIfNotFull(Long userId, double score, long maxQueueSize);

    List<Long> popMin(int count);
}
