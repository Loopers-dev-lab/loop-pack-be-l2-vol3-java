package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "유저 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 유저를 등록합니다."
    )
    ApiResponse<UserV1Dto.SignupResponse> signup(UserV1Dto.SignupRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "로그인한 유저의 정보를 조회합니다. 이름은 마지막 글자가 마스킹됩니다."
    )
    ApiResponse<UserV1Dto.MeResponse> getMe(@Parameter(hidden = true) com.loopers.domain.user.User user);

    @Operation(
        summary = "비밀번호 변경",
        description = "로그인한 유저의 비밀번호를 변경합니다."
    )
    ApiResponse<Void> changePassword(@Parameter(hidden = true) com.loopers.domain.user.User user, UserV1Dto.ChangePasswordRequest request);
}
