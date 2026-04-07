package com.loopers.domain.queue;

/**
 * 입장 스케줄러 틱 관측 훅. Micrometer 연결은 infrastructure에서 빈으로 등록한다.
 */
public interface EntrySchedulerObservation {

    /** {@link EntrySchedulerService#releaseEntries} 호출마다 1회(스케줄된 틱 시도). */
    void onReleaseEntriesInvoked();

    /** 분산 락을 획득하지 못해 이번 틱에서 방출하지 않은 경우. */
    void onLockNotAcquired();

    /**
     * 락을 잡은 뒤 pop·토큰·하트비트까지 완료한 경우 1회.
     *
     * @param releasedCount 이번 틱에 실제 방출·토큰 발급된 인원(대기열이 비면 0)
     */
    void onTickCompleted(int releasedCount);
}
