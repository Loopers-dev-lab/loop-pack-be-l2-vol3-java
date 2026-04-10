package com.loopers.domain.queue;

import java.util.List;

public interface WaitingQueueRepository {

    // 대기열에 진입한다. 이미 존재하면 score(진입 시각)를 갱신하지 않는다.
    long enter(Long userId);

    // 순번 반환 (1부터 시작)
    long getPosition(Long userId);

    // 전체 대기 인원 반환
    long getSize();

    // 스케줄러를 통해 대기열 앞에서 count만큼 꺼낸다
    List<Long> popFront(long count);
}
