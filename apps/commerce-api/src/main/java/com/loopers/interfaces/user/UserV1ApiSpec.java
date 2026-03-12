package com.loopers.interfaces.user;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.user.dto.UserV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "사용자 API 입니다.")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 사용자를 등록합니다."
    )
    ApiResponse<UserV1Dto.SignupResponse> signup(
        @RequestBody(description = "회원가입 요청 정보")
        UserV1Dto.SignupRequest request
    );

    @Operation(
        summary = "내 정보 조회",
        description = "인증된 사용자의 정보를 조회합니다. 헤더에 X-Loopers-LoginId와 X-Loopers-LoginPw를 포함해야 합니다."
    )
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @Parameter(hidden = true)
        LoginUser loginUser
    );

    @Operation(
        summary = "비밀번호 변경",
        description = "인증된 사용자의 비밀번호를 변경합니다. 헤더에 X-Loopers-LoginId와 X-Loopers-LoginPw를 포함해야 합니다."
    )
    ApiResponse<UserV1Dto.ChangePasswordResponse> changePassword(
        @Parameter(hidden = true)
        LoginUser loginUser,
        @RequestBody(description = "비밀번호 변경 요청 정보")
        UserV1Dto.ChangePasswordRequest request
    );
}
