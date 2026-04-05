package com.loopers.application.queue;

public sealed interface QueuePositionResult {

    record Waiting(long rank, long estimatedWaitSeconds, long nextPollAfter)
            implements QueuePositionResult {}

    record Entered(String token)
            implements QueuePositionResult {}
}
