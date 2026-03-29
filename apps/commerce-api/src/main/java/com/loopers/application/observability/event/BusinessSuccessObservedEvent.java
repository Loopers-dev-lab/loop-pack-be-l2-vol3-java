package com.loopers.application.observability.event;

import java.time.Instant;

public record BusinessSuccessObservedEvent(
        String action,
        String domain,
        String className,
        String methodName,
        String traceId,
        String memberId,
        String aggregateId,
        long elapsedMs,
        Instant observedAt
) {
}
