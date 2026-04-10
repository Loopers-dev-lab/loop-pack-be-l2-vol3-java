package com.loopers.interfaces.api.queue;

import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "대기열 API")
public interface QueueV1ApiSpec {

    @Operation(summary = "대기열 진입", description = "인증된 회원이 주문 대기열에 진입합니다. 이미 대기 중이면 현재 순번을 반환합니다.")
    ApiResponse<QueueV1Dto.QueuePositionResponse> enter(@LoginUser UserInfo loginUser);

    @Operation(summary = "순번 조회", description = "현재 대기 순번을 조회합니다. tokenIssued=true이면 주문 API를 호출할 수 있습니다.")
    ApiResponse<QueueV1Dto.QueuePositionResponse> position(@LoginUser UserInfo loginUser);
}
