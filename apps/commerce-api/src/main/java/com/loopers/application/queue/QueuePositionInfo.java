package com.loopers.application.queue;

/**
 * 대기열 순번 조회 응답(애플리케이션 계층). {@code Retry-After}는 HTTP 헤더로만 노출한다.
 */
public record QueuePositionInfo(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String entryToken,
        long suggestedPollIntervalMs,
        long retryAfterSeconds
) {
}
