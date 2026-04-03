package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueuePosition;
import io.swagger.v3.oas.annotations.media.Schema;

public class QueueV1Dto {

    public record QueuePositionResponse(
            @Schema(description = "현재 순번 (1-based). tokenIssued=true이면 0")
            long position,

            @Schema(description = "전체 대기 인원")
            long totalWaiting,

            @Schema(description = "예상 대기 시간 (초)")
            long estimatedWaitSeconds,

            @Schema(description = "true이면 주문 API 진입 가능")
            boolean tokenIssued,

            @Schema(description = "권장 폴링 주기 (ms). tokenIssued=true이면 0")
            int recommendedPollingIntervalMs
    ) {
        public static QueuePositionResponse from(QueuePosition qp) {
            return new QueuePositionResponse(
                    qp.position(),
                    qp.totalWaiting(),
                    qp.estimatedWaitSeconds(),
                    qp.tokenIssued(),
                    qp.recommendedPollingIntervalMs()
            );
        }
    }
}
