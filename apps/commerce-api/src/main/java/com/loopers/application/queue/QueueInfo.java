package com.loopers.application.queue;

public record QueueInfo(
    long position,
    long totalCount,
    long estimatedWaitSeconds,
    String token
) {
}
