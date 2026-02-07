package com.loopers.interfaces.api.auth;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Auth V1 API", description = "인증 관련 API")
public interface AuthV1ApiSpec {

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    ApiResponse<AuthV1Dto.SignupResponse> createUser(AuthV1Dto.SignupRequest request);

    @Operation(summary = "비밀번호 변경", description = "인증된 사용자의 비밀번호를 변경합니다.")
    ApiResponse<Void> updateUserPassword(String loginId, String loginPw, AuthV1Dto.ChangePasswordRequest request);
}
