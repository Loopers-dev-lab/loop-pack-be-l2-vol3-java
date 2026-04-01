package com.loopers.domain.queue;

/** 대기열 진입 결과(동기 순번 또는 비동기 fallback 접수). */
public record JoinQueueResult(
        Long position,
        Long totalWaiting,
        boolean asyncFallbackPending,
        String fallbackRequestId) {

    public JoinQueueResult(Long position, Long totalWaiting) {
        this(position, totalWaiting, false, null);
    }

    /** 동기 대기열 진입 시 생성되는 DTO. */
    public static JoinQueueResult synced(long position, long totalWaiting) {
        return new JoinQueueResult(position, totalWaiting);
    }

    /** 비동기 대기열 진입 의도 수신 시 생성되는 DTO. */
    public static JoinQueueResult asyncAccepted(String fallbackRequestId) {
        return new JoinQueueResult(null, null, true, fallbackRequestId);
    }
}
