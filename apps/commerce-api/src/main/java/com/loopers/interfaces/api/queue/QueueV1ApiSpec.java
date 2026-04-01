package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "주문 대기열 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "주문 대기열에 진입합니다. 중복 진입은 불가합니다.")
    ApiResponse<QueueV1Dto.EnterResponse> enter(QueueV1Dto.EnterRequest request);

    @Operation(summary = "순번 조회", description = "현재 대기 순번과 예상 대기 시간을 조회합니다.")
    ApiResponse<QueueV1Dto.PositionResponse> getPosition(
            @Parameter(description = "유저 ID") Long userId
    );
}
