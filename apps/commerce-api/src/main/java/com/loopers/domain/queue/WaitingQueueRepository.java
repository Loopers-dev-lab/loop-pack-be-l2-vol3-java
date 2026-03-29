package com.loopers.domain.queue;

import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface WaitingQueueRepository {

    boolean enter(Long memberId, double score);

    Optional<Long> getPosition(Long memberId);

    long getTotalCount();

    List<Long> popN(int count);

    List<Map.Entry<Long, Double>> popNWithScore(int count);

    void clear();
}
