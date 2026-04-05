package com.loopers.domain.queue;

public record QueueEntryResult(
        Long userId,
        long position,
        long totalWaiting,
        boolean newEntry
) {}
