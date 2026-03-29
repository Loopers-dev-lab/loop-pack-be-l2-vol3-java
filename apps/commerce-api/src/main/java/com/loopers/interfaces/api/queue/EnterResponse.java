package com.loopers.interfaces.api.queue;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.loopers.application.queue.QueueInfo;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnterResponse(
        long position,
        long estimatedWaitSeconds,
        String token
) {
    public static EnterResponse from(QueueInfo info) {
        return new EnterResponse(info.position(), info.estimatedWaitSeconds(), info.token());
    }
}
