package com.loopers.application.queue;

public record QueueInfo(
        Long rank,
        Long totalWaiting
) {
    public static QueueInfo of(Long rank, Long totalWaiting) {
        return new QueueInfo(rank, totalWaiting);
    }
}
