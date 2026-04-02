package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "주문 대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "주문 대기열에 진입합니다.")
    ApiResponse<QueueV1Dto.EnterQueueResponse> enterQueue(
        @Parameter(hidden = true) AuthenticatedUser authUser
    );

    @Operation(summary = "대기열 위치 조회", description = "대기열 위치 및 입장 토큰 상태를 조회합니다.")
    ApiResponse<QueueV1Dto.PositionResponse> getPosition(
        @Parameter(hidden = true) AuthenticatedUser authUser
    );
}
