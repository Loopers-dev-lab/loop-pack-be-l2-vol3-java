package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Queue V1 API", description = "블랙프라이데이 대기열 API")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "대기열에 진입하고 폴링용 토큰을 발급받습니다. 이미 진입한 경우 순위만 갱신됩니다."
    )
    ApiResponse<QueueV1Dto.EnterResponse> enter(QueueV1Dto.EnterRequest request);

    @Operation(
        summary = "대기열 상태 조회 (폴링)",
        description = """
            현재 순위와 예상 대기 시간을 반환합니다.
            admitted=true이면 서비스 이용이 가능합니다.

            pollIntervalHint 필드로 서버가 권고 폴링 주기를 제공합니다.
            (Thundering Herd 방지를 위해 Jitter 추가 적용 권장)
            """
    )
    ApiResponse<QueueV1Dto.StatusResponse> getStatus(String token, String queueId);

    @Operation(
        summary = "대기열 순번 SSE 구독",
        description = """
            Server-Sent Events(SSE)로 입장 허가 알림을 실시간으로 수신합니다.
            연결 즉시 현재 상태(queue-status)를 push합니다.
            스케줄러가 입장 허가 시 queue-admitted 이벤트를 push합니다.

            폴링 대비 장점: 입장 허가 즉시 알림, 불필요한 요청 감소.
            timeout: 5분. 만료 시 클라이언트가 재연결해야 합니다.
            """
    )
    SseEmitter streamStatus(String token, String queueId);
}
