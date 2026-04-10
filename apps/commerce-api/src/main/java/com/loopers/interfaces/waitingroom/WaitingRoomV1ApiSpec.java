package com.loopers.interfaces.waitingroom;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.waitingroom.dto.WaitingRoomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "WaitingRoom V1 API", description = "대기열 API 입니다.")
public interface WaitingRoomV1ApiSpec {

    @Operation(
        summary = "대기열 진입",
        description = "유저를 대기열에 등록합니다. 이미 대기 중이면 기존 순번을 반환합니다 (멱등)."
    )
    ApiResponse<WaitingRoomResponse.PositionResponse> enter(
        @Parameter(hidden = true) LoginUser loginUser
    );

    @Operation(
        summary = "순번 조회 (Polling)",
        description = "현재 대기 상태를 조회합니다. "
                + "WAITING: 대기 중 (순번 + 예상 대기시간), "
                + "ENTERED: 입장 가능 (토큰 포함), "
                + "NOT_IN_QUEUE: 대기열 미진입. "
                + "클라이언트는 2~3초 간격으로 Polling합니다."
    )
    ApiResponse<WaitingRoomResponse.PositionResponse> position(
        @Parameter(hidden = true) LoginUser loginUser
    );

    @Operation(
        summary = "대기열 취소",
        description = "대기열에서 나갑니다. 대기열에 없어도 에러가 발생하지 않습니다."
    )
    ApiResponse<Object> cancel(
        @Parameter(hidden = true) LoginUser loginUser
    );
}
