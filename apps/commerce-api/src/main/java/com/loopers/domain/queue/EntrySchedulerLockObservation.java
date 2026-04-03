package com.loopers.domain.queue;

/**
 * 입장 스케줄러 틱에서 분산 락을 획득하지 못해 방출을 건너뛸 때의 관측 훅.
 * <p>
 * 도메인은 Micrometer에 의존하지 않기 위해 인터페이스만 둔다. 구현은 infrastructure에서
 * {@code @Bean} 메서드 레퍼런스 등으로 주입한다.
 */
@FunctionalInterface
public interface EntrySchedulerLockObservation {

    void onLockNotAcquired();
}
