package com.loopers.application.queue.dto;

import com.loopers.domain.queue.service.QueueService;

public record QueuePositionResDto(
        Long rank,
        Long totalWaiting,
        int estimatedWaitSeconds,
        String token,
        Long delayMs,
        Long nextPollAfterMs
) {

    public static QueuePositionResDto from(QueueService.QueuePositionInfo info) {
        return new QueuePositionResDto(
                info.rank(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                null,
                null,
                info.nextPollAfterMs()
        );
    }

    public static QueuePositionResDto withToken(String token, Long delayMs) {
        return new QueuePositionResDto(null, null, 0, token, delayMs, null);
    }

    public static QueuePositionResDto bypass() {
        return new QueuePositionResDto(null, null, 0, "bypass", null, null);
    }
}
