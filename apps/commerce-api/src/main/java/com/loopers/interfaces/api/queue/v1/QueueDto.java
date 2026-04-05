package com.loopers.interfaces.api.queue.v1;

import com.loopers.application.queue.QueuePositionResult;

public class QueueDto {

    /**
     * 대기열 순번 조회 응답.
     *
     * @param position             1-based 대기 순번 (입장 완료 시 0)
     * @param totalWaiting         전체 대기 인원
     * @param estimatedWaitSeconds 예상 대기 시간(초)
     * @param pollingIntervalMs    클라이언트 권장 폴링 주기(밀리초, 입장 완료 시 0)
     * @param token                입장 토큰 (대기 중이면 null)
     */
    public record PositionResponse(
            long position,
            long totalWaiting,
            long estimatedWaitSeconds,
            long pollingIntervalMs,
            String token
    ) {

        public static PositionResponse from(QueuePositionResult result) {
            return new PositionResponse(
                    result.position(),
                    result.totalWaiting(),
                    result.estimatedWaitSeconds(),
                    result.pollingIntervalMs(),
                    result.token()
            );
        }
    }
}
