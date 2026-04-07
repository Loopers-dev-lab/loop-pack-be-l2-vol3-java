package com.loopers.application.queue;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 순번 SSE 이벤트 본문용 application 계층 DTO(JSON 직렬화).
 * {@code interfaces}의 {@code QueueV1Dto.PositionResponse}와 필드가 비슷해도 채널·계층이 달라 별도 타입으로 둔다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record QueuePositionSseDto(
        long position,
        long totalWaiting,
        long estimatedWaitSeconds,
        String entryToken,
        long suggestedPollIntervalMs
) {
    static QueuePositionSseDto from(QueuePositionInfo info) {
        return new QueuePositionSseDto(
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.entryToken(),
                info.suggestedPollIntervalMs()
        );
    }
}
