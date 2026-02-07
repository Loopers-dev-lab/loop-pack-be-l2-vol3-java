package com.loopers.interfaces.api.user;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "User V1 API", description = "사용자 정보 관련 API")
public interface UserV1ApiSpec {

    @Operation(summary = "내 정보 조회", description = "인증된 사용자의 정보를 조회합니다.")
    ApiResponse<UserV1Dto.UserResponse> getUser(String loginId, String loginPw);
}
