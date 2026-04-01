package com.loopers.application.queue;

public record QueuePositionInfo(
        long position,
        long totalCount,
        long estimatedWaitSeconds,
        long nextPollAfterSeconds,
        String token
) {
}