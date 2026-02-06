package com.loopers.interfaces.api.user.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.v1.UserV1Dto.UpdatePasswordRequest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "사용자 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
            summary = "회원가입",
            description = "새로운 사용자를 등록합니다."
    )
    ApiResponse<UserV1Dto.SignUpResponse> signUp(
            @Schema(description = "회원가입 요청 정보")
            UserV1Dto.SignUpRequest request
    );

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 사용자의 정보를 조회합니다."
    )
    ApiResponse<UserV1Dto.MeResponse> getMyInfo(Long userId);

    @Operation(
            summary = "비밀번호 수정",
            description = "로그인한 사용자의 비밀번호를 수정합니다."
    )
    ApiResponse<Object> updatePassword(
            Long userId,
            @Schema(description = "비밀번호 수정 요청 정보")
            UpdatePasswordRequest request
    );
}
