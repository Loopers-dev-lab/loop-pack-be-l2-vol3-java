package com.loopers.support.queue;

import java.util.List;

/**
 * 대기열에서 입장열로의 전환을 위한 포트 인터페이스.
 *
 * <p>대기열 조회 → 토큰 발급(SET NX) → 대기열 제거를 순차 실행한다.
 * SET NX의 멱등성으로 중간 장애 시에도 다음 사이클에서 자연 복구된다.</p>
 */
public interface WaitingQueueAdmitter {

    /**
     * 대기열 선두에서 최대 count명을 입장열로 멱등하게 이동시킨다.
     * 부분 실패 시 다음 스케줄링 사이클에서 복구된다.
     *
     * @param count 이동할 최대 인원 수
     * @return 이동된 사용자 ID 목록
     */
    List<Long> admit(int count);
}
