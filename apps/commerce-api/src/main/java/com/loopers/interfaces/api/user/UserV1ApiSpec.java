package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
}
