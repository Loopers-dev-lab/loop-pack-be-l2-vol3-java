package com.loopers.domain.waitingroom;

import java.util.List;

public interface WaitingQueue {

    boolean enter(Long userId);

    Long getRank(Long userId);

    long getTotalWaiting();

    boolean cancel(Long userId);

    List<Long> popFront(int count);

    List<WaitingEntry> popFrontWithScores(int count);
}


