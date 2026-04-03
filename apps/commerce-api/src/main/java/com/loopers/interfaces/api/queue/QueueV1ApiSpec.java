package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

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

            권장 폴링 인터벌:
            - rank 1~100: 1초
            - rank 101~1000: 3초
            - rank 1000+: 5초
            (Thundering Herd 방지를 위해 Jitter 적용 권장)
            """
    )
    ApiResponse<QueueV1Dto.StatusResponse> getStatus(String token, String queueId);
}
