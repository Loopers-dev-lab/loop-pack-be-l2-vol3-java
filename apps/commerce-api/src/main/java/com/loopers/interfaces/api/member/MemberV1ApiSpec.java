package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member V1 API", description = "회원 관리 API 입니다.")
public interface MemberV1ApiSpec {

    @Operation(summary = "회원가입")
    ApiResponse<MemberV1Dto.RegisterResponse> register(MemberV1Dto.RegisterRequest request);
}
