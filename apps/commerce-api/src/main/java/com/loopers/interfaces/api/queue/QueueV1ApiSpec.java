package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "대기열 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "userId로 대기열에 진입합니다. 이미 진입한 경우 기존 순번을 반환합니다.")
    ApiResponse<QueueV1Dto.EnterResponse> enter(String userId);

    @Operation(summary = "순번 조회", description = "현재 대기 순번과 전체 대기 인원을 반환합니다.")
    ApiResponse<QueueV1Dto.PositionResponse> getPosition(String userId);
}