package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue", description = "대기열 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "이벤트 대기열에 진입합니다.")
    ApiResponse<QueueV1Dto.QueuePositionResponse> enter(
        @Parameter(description = "이벤트 ID") String eventId,
        @Parameter(description = "유저 ID", required = true) Long userId
    );

    @Operation(summary = "순번 조회", description = "현재 대기 순번과 예상 대기시간을 조회합니다.")
    ApiResponse<QueueV1Dto.QueuePositionResponse> getPosition(
        @Parameter(description = "이벤트 ID") String eventId,
        @Parameter(description = "유저 ID", required = true) Long userId
    );
}
