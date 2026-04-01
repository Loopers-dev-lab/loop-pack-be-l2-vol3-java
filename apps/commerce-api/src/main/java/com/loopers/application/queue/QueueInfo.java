package com.loopers.application.queue;

public record QueueInfo(
        Long position,
        Long totalWaiting,
        boolean asyncFallbackPending,
        String fallbackRequestId
) {
    /** 동기 대기열 진입 시 생성되는 DTO. */
    public QueueInfo(Long position, Long totalWaiting) {
        this(position, totalWaiting, false, null);
    }

    /** 비동기 대기열 진입 의도 수신 시 생성되는 DTO. */
    public static QueueInfo asyncAccepted(String fallbackRequestId) {
        return new QueueInfo(null, null, true, fallbackRequestId);
    }
}
