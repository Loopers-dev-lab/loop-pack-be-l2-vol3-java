package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "대기열 관련 API입니다.")
public interface QueueV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "유저를 대기열에 추가합니다. 이미 입장 토큰이 있는 경우 토큰 정보를 반환합니다."
    )
    ApiResponse<QueueV1Dto.QueueEnterResponse> enter(QueueV1Dto.QueueEnterRequest request);

    @Operation(
        summary = "순번 조회",
        description = "대기열에서 유저의 현재 순번과 예상 대기 시간을 조회합니다."
    )
    ApiResponse<QueueV1Dto.QueuePositionResponse> getPosition(
        @Parameter(description = "유저 ID", required = true) Long userId
    );

    @Operation(
        summary = "대기열 이탈",
        description = "유저가 자발적으로 대기열에서 나갑니다. 이미 토큰이 발급된 상태라면 토큰도 함께 삭제됩니다."
    )
    ApiResponse<QueueV1Dto.QueueLeaveResponse> leave(
        @Parameter(description = "유저 ID", required = true) Long userId
    );
}