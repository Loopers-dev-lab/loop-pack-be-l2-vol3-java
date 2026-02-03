package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member V1 API", description = "회원 관리 API 입니다.")
public interface MemberV1ApiSpec {

    @Operation(summary = "회원가입")
    ApiResponse<MemberV1Dto.RegisterResponse> register(MemberV1Dto.RegisterRequest request);

    @Operation(summary = "내 정보 조회")
    ApiResponse<MemberV1Dto.MemberResponse> getMyInfo(String loginId, String password);

    @Operation(summary = "비밀번호 수정")
    ApiResponse<Object> changePassword(String loginId, String currentPassword, MemberV1Dto.ChangePasswordRequest request);
}
