package com.loopers.interfaces.api.queue;

public class QueueDto {

    public record EnterResponse(long position) {}

    public record SizeResponse(long size) {}

    public record PositionResponse(long position, long estimatedWaitSeconds, boolean tokenIssued, long nextPollIntervalMs) {}
}
