package com.loopers.domain.queue;

// 대기열 시스템에서 공통으로 사용하는 상수를 정의하는 클래스.
// 스케줄러의 배치 처리 단위, 실행 주기, 피처 플래그 키를 한곳에서 관리하여
// 대기 시간 계산(QueuePositionInfo)과 스케줄러(QueueScheduler)의 설정 일관성을 보장한다.
public final class QueueConstants {

    // --- Redis 키 ---

    // 대기열 Sorted Set의 Redis 키.
    // 모든 대기열 관련 코드에서 이 상수를 참조하여 키 변경 시 한 곳만 수정하면 된다.
    public static final String QUEUE_KEY = "waiting-queue";

    // 입장 토큰의 Redis 키 접두사. 실제 키는 "entry-token:{userId}" 형태.
    public static final String TOKEN_KEY_PREFIX = "entry-token:";

    // --- 스케줄러 설정 ---

    // 스케줄러가 한 번 실행될 때 대기열에서 꺼내 토큰을 발급하는 최대 유저 수.
    // 예: BATCH_SIZE=18, INTERVAL_MS=100 → 초당 최대 180명 처리
    public static final int BATCH_SIZE = 18;

    // 스케줄러 실행 주기(밀리초).
    // application.yml의 queue.scheduler.fixed-delay와 동일한 값을 유지해야 한다.
    // QueuePositionInfo의 예상 대기 시간 계산에도 사용된다.
    public static final long INTERVAL_MS = 100;

    // --- Feature Flag ---

    // feature_flag 테이블에서 대기열 활성화 여부를 조회할 때 사용하는 키.
    // 이 키에 해당하는 플래그가 enabled=true일 때만 대기열이 동작한다.
    public static final String QUEUE_FEATURE_KEY = "QUEUE_ENABLED";

    private QueueConstants() {
    }
}
