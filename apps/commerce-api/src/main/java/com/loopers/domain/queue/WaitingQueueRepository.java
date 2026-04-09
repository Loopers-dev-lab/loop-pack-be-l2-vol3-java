package com.loopers.domain.queue;

import java.util.Set;

public interface WaitingQueueRepository {
    /**
     * 대기열에 유저를 추가한다.
     * @return true면 새로 추가됨, false면 이미 존재 (중복 진입 방지)
     */
    boolean enqueue(Long userId, double score);

    /** 유저의 현재 순번을 조회한다. (0-based, null이면 대기열에 없음) */
    Long getRank(Long userId);

    /** 전체 대기 인원을 조회한다. */
    long getTotalCount();

    /** 앞에서 count명을 꺼낸다. (ZPOPMIN) */
    Set<Long> dequeue(int count);
}
