package com.loopers.domain.queue;

/**
 * 대기열 입장 스케줄러의 분산 락·하트비트 저장소 포트.
 * 구현체는 보통 Redis SET NX + TTL(락) 및 SET + TTL(하트비트)로 제공한다.
 */
public interface SchedulerLockRepository {

    /**
     * 키가 없을 때만 값을 설정해 락을 획득한다. 이미 키가 있으면 false.
     *
     * @param lockKey    락 식별 키 (예: {@code queue:scheduler:lock})
     * @param lockValue  락 소유자 식별용 값 (해제 시 검증에 쓸 수 있음; 현재 스케줄러는 획득만 사용)
     * @param ttlSeconds 락 만료(초). 장애로 인한 무한 점유 방지
     * @return 락 획득 성공 여부
     */
    boolean tryAcquireLock(String lockKey, String lockValue, long ttlSeconds);

    /**
     * 스케줄러가 마지막으로 성공 틱을 돈 시각 등을 저장한다. 모니터링·헬스 확인용.
     *
     * @param heartbeatKey    하트비트 키 (예: {@code queue:scheduler:heartbeat})
     * @param value           저장할 문자열 (예: epoch millis)
     * @param ttlSeconds      값 TTL(초). 갱신이 끊기면 키가 사라져 장애 감지에 활용 가능
     */
    void updateHeartbeat(String heartbeatKey, String value, long ttlSeconds);
}
