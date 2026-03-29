package com.loopers.interfaces.api.queue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loopers.application.queue.QueueInfo;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnterResponse(
        String status,
        long position,
        long estimatedWaitSeconds,
        long totalInQueue,
        String token,
        Long estimateA,
        Long estimateB,
        Long estimateC
) {
    public static EnterResponse from(QueueInfo info) {
        return new EnterResponse(
                info.status().name(),
                info.position(),
                info.estimatedWaitSeconds(),
                info.totalInQueue(),
                info.token(),
                info.estimateA(),
                info.estimateB(),
                info.estimateC()
        );
    }
}
