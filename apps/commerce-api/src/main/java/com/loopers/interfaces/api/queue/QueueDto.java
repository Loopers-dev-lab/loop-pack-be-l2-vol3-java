package com.loopers.interfaces.api.queue;

public class QueueDto {

    public record EnterResponse(
        String status,
        Long position,
        Long estimatedWaitSeconds,
        Long tokenRemainingSeconds
    ) {}

    public record PositionResponse(
        String status,
        Long position,
        Long totalQueueSize,
        Long estimatedWaitSeconds,
        Long tokenRemainingSeconds
    ) {}
}
