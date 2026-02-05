package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User API", description = "회원 관리 API")
public interface UserApiV1Spec {

    @Operation(
            summary = "회원가입",
            description = "새로운 회원을 등록합니다. 이름은 마지막 글자가 마스킹되어 반환됩니다."
    )
    ApiResponse<UserV1Dto.UserResponse> signUp(UserV1Dto.SignUpRequest request);

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 회원의 정보를 조회합니다. 이름은 마지막 글자가 마스킹되어 반환됩니다."
    )
    ApiResponse<UserV1Dto.UserResponse> getMyInfo(
            @Parameter(description = "로그인 ID") String loginId,
            @Parameter(description = "비밀번호") String password
    );

    @Operation(
            summary = "비밀번호 변경",
            description = "회원의 비밀번호를 변경합니다."
    )
    ApiResponse<Object> changePassword(
            @Parameter(description = "로그인 ID") String loginId,
            @Parameter(description = "현재 비밀번호") String password,
            UserV1Dto.ChangePasswordRequest request
    );
}
