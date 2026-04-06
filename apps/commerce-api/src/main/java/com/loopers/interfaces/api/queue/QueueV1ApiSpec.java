package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.queue.dto.QueuePositionApiResDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Queue V1 API", description = "대기열 API 입니다.")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "주문 대기열에 진입합니다.")
    ApiResponse<QueuePositionApiResDto> enterQueue(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password
    );

    @Operation(summary = "대기열 순번 조회", description = "현재 대기 순번과 예상 대기시간을 조회합니다.")
    ApiResponse<QueuePositionApiResDto> getPosition(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password
    );
}
