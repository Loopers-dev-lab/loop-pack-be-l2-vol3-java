package com.loopers.interfaces.api.queue.v1;

import com.loopers.interfaces.api.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(
            summary = "대기열 진입",
            description = "주문 대기열에 진입합니다. 이미 대기 중인 경우 409 에러를 반환합니다."
    )
    ApiResponse<Object> enterQueue(Long userId);
}
