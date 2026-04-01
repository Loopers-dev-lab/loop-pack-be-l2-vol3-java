package com.loopers.support.queue;

import java.util.List;

/**
 * 대기열에서 입장열로의 원자적 전환을 위한 포트 인터페이스.
 *
 * <p>대기열 조회 → 입장열 추가 → 대기열 제거를 하나의 원자적 연산으로 수행하여
 * 중간 장애로 인한 상태 불일치를 방지한다.</p>
 */
public interface WaitingQueueAdmitter {

    /**
     * 대기열 선두에서 최대 count명을 입장열로 원자적으로 이동시킨다.
     *
     * @param count 이동할 최대 인원 수
     * @return 이동된 사용자 ID 목록
     */
    List<Long> admit(int count);
}
