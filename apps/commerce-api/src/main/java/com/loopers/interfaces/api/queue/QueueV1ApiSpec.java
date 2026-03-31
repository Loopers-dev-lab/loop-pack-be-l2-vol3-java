package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "대기열 API")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "대기열에 진입하고 현재 순번 및 총 대기 인원을 반환합니다."
    )
    ApiResponse<QueueV1Dto.JoinQueueResponse> joinQueue(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId
    );
}

