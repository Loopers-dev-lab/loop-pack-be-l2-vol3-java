package com.loopers.domain.queue;

// 스케줄러 분산 락 저장소 인터페이스. Domain 레이어에 정의하여 DIP를 적용한다.
// 구현체에서 DB의 원자적 UPDATE를 사용하여 동시 접근을 제어한다.
public interface SchedulerLockRepository {

    // 락 획득을 시도한다.
    // locked=false이거나 locked_at이 만료 시간을 초과한 경우에만 획득 성공(true).
    // 원자적 UPDATE로 동시 접근 시 하나의 인스턴스만 성공한다.
    boolean tryAcquire(String lockKey, String instanceId, long expireSeconds);

    // 락을 해제한다. 스케줄러 실행 완료 후 호출한다.
    void release(String lockKey);
}
