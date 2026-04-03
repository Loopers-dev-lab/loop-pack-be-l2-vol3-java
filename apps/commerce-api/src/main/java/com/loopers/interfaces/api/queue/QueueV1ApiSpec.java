package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Queue V1 API", description = "대기열 API")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "대기열에 진입하고 현재 순번 및 총 대기 인원을 반환합니다. "
                + "Redis 장애 시 Kafka 비동기 접수(asyncFallbackPending=true, fallbackRequestId)로 응답할 수 있습니다. "
                + "설정된 최대 대기 인원(queue.join.max-waiting)을 초과하면 409(CONFLICT)로 진입을 거절합니다."
    )
    ApiResponse<QueueV1Dto.JoinQueueResponse> joinQueue(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId
    );

    @Operation(
            summary = "대기열 순번 조회",
            description = "현재 순번·예상 대기·입장 토큰(있을 때)·폴링 힌트를 반환합니다. "
                    + "Retry-After 헤더(초)와 suggestedPollIntervalMs를 함께 제공합니다."
    )
    ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> getQueuePosition(
            @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
            String loginId
    );

    @Operation(
            summary = "대기열 순번 SSE",
            description = "순번 스냅샷을 주기적으로 text/event-stream으로 전송합니다."
    )
    SseEmitter streamQueuePosition(
            @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
            String loginId
    );
}

