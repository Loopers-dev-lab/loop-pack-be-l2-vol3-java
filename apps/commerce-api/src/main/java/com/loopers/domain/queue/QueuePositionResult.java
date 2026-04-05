package com.loopers.domain.queue;

public record QueuePositionResult(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        int pollingIntervalSeconds,
        String token
) {}
