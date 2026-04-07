package com.loopers.domain.queue;

/**
 * 대기열 순번 조회 결과
 *
 * status가 ACTIVE일 때 tokenRemainingSeconds에 토큰 잔여 시간이 포함된다.
 * 클라이언트는 status=ACTIVE를 받으면 주문 API 호출이 가능하다.
 */
public record QueuePositionResult(
        QueueStatus status,
        long position,
        long totalWaiting,
        int estimatedWaitSeconds,
        int nextPollAfterMs,
        long tokenRemainingSeconds
) {

    public static QueuePositionResult waiting(long position, long totalWaiting,
                                               int estimatedWaitSeconds, int nextPollAfterMs) {
        return new QueuePositionResult(QueueStatus.WAITING, position, totalWaiting,
                estimatedWaitSeconds, nextPollAfterMs, 0);
    }

    public static QueuePositionResult active(long tokenRemainingSeconds) {
        return new QueuePositionResult(QueueStatus.ACTIVE, 0, 0, 0, 0, tokenRemainingSeconds);
    }

    public static QueuePositionResult notInQueue() {
        return new QueuePositionResult(QueueStatus.NOT_IN_QUEUE, 0, 0, 0, 0, 0);
    }
}
