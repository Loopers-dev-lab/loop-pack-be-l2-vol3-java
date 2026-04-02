package com.loopers.interfaces.api.queue.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(
            summary = "대기열 진입",
            description = "주문 대기열에 진입합니다. 이미 대기 중인 경우에도 정상 응답을 반환합니다."
    )
    ApiResponse<QueueDto.PositionResponse> enterQueue(Long userId);

    @Operation(
            summary = "대기열 순번 조회",
            description = "현재 대기열에서의 순번, 전체 대기 인원, 예상 대기 시간, 권장 폴링 주기를 조회합니다. "
                    + "pollingIntervalMs는 순번 구간에 따라 1초~20초로 동적 산정되며, 입장 완료 시 0을 반환합니다."
    )
    ApiResponse<QueueDto.PositionResponse> getPosition(Long userId);
}
