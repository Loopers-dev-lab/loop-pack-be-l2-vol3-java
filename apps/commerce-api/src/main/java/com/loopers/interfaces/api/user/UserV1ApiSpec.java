package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "회원 관련 API")
public interface UserV1ApiSpec {

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    ApiResponse<UserV1Dto.UserResponse> signup(UserV1Dto.SignupRequest request);

    @Operation(summary = "내 정보 조회", description = "인증된 사용자의 정보를 조회합니다.")
    ApiResponse<UserV1Dto.UserResponse> getMyInfo(String loginId, String loginPw);

    @Operation(summary = "비밀번호 수정", description = "인증된 사용자의 비밀번호를 변경합니다.")
    ApiResponse<Void> changePassword(String loginId, String loginPw, UserV1Dto.ChangePasswordRequest request);
}
