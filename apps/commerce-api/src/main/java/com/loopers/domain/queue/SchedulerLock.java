package com.loopers.domain.queue;

/**
 * 스케줄러 리더 선출용 분산락 인터페이스.
 *
 * <p>다중 인스턴스 환경에서 대기열 스케줄러의 이중 실행을 방지하기 위한 도메인 계약.
 * infrastructure 계층에서 Redis 등으로 구현한다.</p>
 *
 * @see OrderQueueScheduler
 */
public interface SchedulerLock {

    /**
     * 리더 선출 락 획득을 시도한다.
     *
     * @return true: 획득 성공 (이번 주기 실행), false: 다른 인스턴스가 실행 중
     */
    boolean tryAcquire();

    /**
     * 리더 선출 락을 해제한다.
     * 본인이 획득한 락만 해제한다 (owner 검증).
     */
    void release();
}
