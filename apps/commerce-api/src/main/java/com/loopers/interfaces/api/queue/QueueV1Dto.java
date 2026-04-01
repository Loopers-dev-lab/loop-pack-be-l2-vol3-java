package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.EnterResult;
import com.loopers.domain.queue.QueuePosition;
import com.loopers.support.enums.QueueStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 대기열 API DTO. 기존 {@code OrderV1Dto} inner static class 패턴.
 */
public class QueueV1Dto {

    // === 대기열 진입 응답 ===

    @Getter
    @Builder
    public static class EnterResponse {
        private final long position;
        private final long totalWaiting;
        private final int estimatedWaitSeconds;
        private final boolean alreadyEntered;

        /**
         * EnterResult → EnterResponse 변환.
         *
         * @param result QueueService.enter() 반환값
         */
        public static EnterResponse from(EnterResult result) {
            return EnterResponse.builder()
                    .position(result.position().position())
                    .totalWaiting(result.position().totalWaiting())
                    .estimatedWaitSeconds(result.position().estimatedWaitSeconds())
                    .alreadyEntered(!result.isNew())
                    .build();
        }
    }

    // === 순번 조회 응답 ===

    @Getter
    @Builder
    public static class PositionResponse {
        private final QueueStatus status;
        private final long position;
        private final long totalWaiting;
        private final int estimatedWaitSeconds;
        private final String token;

        /**
         * QueuePosition → PositionResponse 변환.
         *
         * @param pos QueueService.getPosition() 반환값
         */
        public static PositionResponse from(QueuePosition pos) {
            return PositionResponse.builder()
                    .status(pos.status())
                    .position(pos.position())
                    .totalWaiting(pos.totalWaiting())
                    .estimatedWaitSeconds(pos.estimatedWaitSeconds())
                    .token(pos.token())
                    .build();
        }
    }
}
