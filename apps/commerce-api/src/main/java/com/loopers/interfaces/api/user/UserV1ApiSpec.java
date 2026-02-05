package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User", description = "사용자 API")
public interface UserV1ApiSpec {

    @Operation(summary = "회원가입", description = "새로운 사용자를 등록합니다.")
    ApiResponse<UserV1Dto.RegisterResponse> register(UserV1Dto.RegisterRequest request);

    @Operation(summary = "내 정보 조회", description = "로그인한 사용자의 정보를 조회합니다. 이름은 마스킹 처리됩니다.")
    ApiResponse<UserV1Dto.UserResponse> getMe(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password
    );

    @Operation(summary = "비밀번호 변경", description = "비밀번호를 변경합니다.")
    ApiResponse<Void> changePassword(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        UserV1Dto.ChangePasswordRequest request
    );
}
