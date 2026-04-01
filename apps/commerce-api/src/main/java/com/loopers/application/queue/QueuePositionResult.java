package com.loopers.application.queue;

public record QueuePositionResult(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String token
) {

}
