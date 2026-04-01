package com.loopers.application.queue;

/**
 * 대기열 순번 조회 결과.
 *
 * @param position             1-based 대기 순번 (입장 완료 시 0)
 * @param totalWaiting         전체 대기 인원
 * @param estimatedWaitSeconds 예상 대기 시간(초)
 * @param pollingIntervalMs    클라이언트 권장 폴링 주기(밀리초, 입장 완료 시 0)
 * @param token                입장 토큰 (대기 중이면 null)
 */
public record QueuePositionResult(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        long pollingIntervalMs,
        String token
) {

    /**
     * 입장 허용된 사용자용 결과를 생성한다. 폴링이 불필요하므로 {@code pollingIntervalMs}는 0이다.
     */
    public static QueuePositionResult admitted(String token) {
        return new QueuePositionResult(0, 0, 0, 0, token);
    }
}
