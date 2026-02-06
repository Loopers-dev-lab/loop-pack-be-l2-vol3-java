package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "User V1 API", description = "사용자 관리 API")
public interface UserV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 사용자를 등록합니다."
    )
    ApiResponse<UserV1Dto.SignUpResponse> signUp(
        @Schema(description = "회원가입 요청 정보")
        @Valid UserV1Dto.SignUpRequest request
    );

    @Operation(
        summary = "내 정보 조회",
        description = "로그인한 사용자의 정보를 조회합니다."
    )
    ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(
        @Parameter(description = "로그인 사용자 ID", required = true)
        String loginId
    );

    @Operation(
        summary = "비밀번호 변경",
        description = "사용자의 비밀번호를 변경합니다."
    )
    ApiResponse<Void> updatePassword(
        @Parameter(description = "로그인 사용자 ID", required = true)
        String loginId,
        @Schema(description = "비밀번호 변경 요청 정보")
        @Valid UserV1Dto.UpdatePasswordRequest request
    );
}
