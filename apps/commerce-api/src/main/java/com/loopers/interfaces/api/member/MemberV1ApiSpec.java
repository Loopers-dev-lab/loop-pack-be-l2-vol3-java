package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member V1 API", description = "회원 API 입니다.")
public interface MemberV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 회원을 등록합니다."
    )
    ApiResponse<MemberV1Dto.SignUpResponse> signUp(MemberV1Dto.SignUpRequest request);

    @Operation(
        summary = "내 정보 조회",
        description = "인증된 회원의 정보를 조회합니다."
    )
    ApiResponse<MemberV1Dto.MyInfoResponse> getMyInfo(String loginId, String password);
}