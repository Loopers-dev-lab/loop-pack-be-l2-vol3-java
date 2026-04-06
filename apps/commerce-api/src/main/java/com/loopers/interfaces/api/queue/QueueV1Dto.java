package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueLeaveResult;
import com.loopers.application.queue.QueuePositionResult;
import com.loopers.application.queue.QueueResult;
import jakarta.validation.constraints.NotNull;

public class QueueV1Dto {

    public record QueueEnterRequest(
        @NotNull(message = "userId는 필수입니다.")
        Long userId
    ) {}

    public record QueueEnterResponse(
        Long userId,
        int position,
        int estimatedWaitSeconds,
        String status,
        String token
    ) {
        public static QueueEnterResponse from(QueueResult result) {
            return new QueueEnterResponse(
                result.userId(),
                result.position(),
                result.estimatedWaitSeconds(),
                result.status().name(),
                result.token()
            );
        }
    }

    public record QueuePositionResponse(
        Long userId,
        int position,
        int estimatedWaitSeconds,
        long totalWaiting,
        String status,
        String token
    ) {
        public static QueuePositionResponse from(QueuePositionResult result) {
            return new QueuePositionResponse(
                result.userId(),
                result.position(),
                result.estimatedWaitSeconds(),
                result.totalWaiting(),
                result.status().name(),
                result.token()
            );
        }
    }

    public record QueueLeaveResponse(
        Long userId,
        String status
    ) {
        public static QueueLeaveResponse from(QueueLeaveResult result) {
            return new QueueLeaveResponse(
                result.userId(),
                result.status().name()
            );
        }
    }
}