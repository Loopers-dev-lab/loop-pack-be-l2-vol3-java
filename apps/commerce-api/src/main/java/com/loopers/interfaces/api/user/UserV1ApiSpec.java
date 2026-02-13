package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "회원 관련 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 회원을 등록합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> signup(UserV1Dto.SignupRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "인증된 사용자의 정보를 조회합니다."
    )
    ApiResponse<UserV1Dto.UserResponse> getMe(String loginId);

    @Operation(
        summary = "비밀번호 변경",
        description = "인증된 사용자의 비밀번호를 변경합니다."
    )
    ApiResponse<Object> changePassword(String loginId, UserV1Dto.ChangePasswordRequest request);
}
