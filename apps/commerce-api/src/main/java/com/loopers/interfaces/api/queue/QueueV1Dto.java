package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueuePositionInfo;
import com.loopers.application.queue.QueueStatus;

public class QueueV1Dto {

    public record QueuePositionResponse(
        Long userId,
        QueueStatus status,
        Long position,
        Long totalWaiting,
        Long estimatedWaitSeconds,
        String token,
        Integer recommendedPollingSeconds
    ) {
        public static QueuePositionResponse from(QueuePositionInfo info) {
            return new QueuePositionResponse(
                info.userId(),
                info.status(),
                info.position(),
                info.totalWaiting(),
                info.estimatedWaitSeconds(),
                info.token(),
                info.recommendedPollingSeconds()
            );
        }
    }

    public record QueueCountResponse(Long totalWaiting) {
    }
}
