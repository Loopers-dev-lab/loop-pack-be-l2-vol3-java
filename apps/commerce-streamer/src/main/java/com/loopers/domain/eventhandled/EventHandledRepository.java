package com.loopers.domain.eventhandled;

/**
 * 이벤트 처리 이력을 관리하는 Port.
 *
 * <p>Consumer에서 중복 이벤트를 필터링하기 위해 사용한다.
 * 구현체는 Redis SETNX 등 원자적 연산으로 확인과 등록을 동시에 수행한다.</p>
 */
public interface EventHandledRepository {

    /**
     * 이벤트 ID를 처리 완료로 등록한다. 이미 등록된 경우 false를 반환한다.
     *
     * @param eventId 이벤트 식별자
     * @return 새로 등록되면 true, 이미 존재하면 false
     */
    boolean markIfAbsent(String eventId);
}
