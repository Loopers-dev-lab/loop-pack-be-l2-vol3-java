package com.loopers.application.queue;

public record QueueInfo(
        long position,
        long estimatedWaitSeconds,
        String token
) {
}
