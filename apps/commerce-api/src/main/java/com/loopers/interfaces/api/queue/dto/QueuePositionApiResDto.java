package com.loopers.interfaces.api.queue.dto;

import com.loopers.application.queue.dto.QueuePositionResDto;

public record QueuePositionApiResDto(
        Long rank,
        Long totalWaiting,
        int estimatedWaitSeconds,
        String token,
        Long delayMs,
        Long nextPollAfterMs
) {

    public static QueuePositionApiResDto from(QueuePositionResDto dto) {
        return new QueuePositionApiResDto(
                dto.rank(),
                dto.totalWaiting(),
                dto.estimatedWaitSeconds(),
                dto.token(),
                dto.delayMs(),
                dto.nextPollAfterMs()
        );
    }
}
