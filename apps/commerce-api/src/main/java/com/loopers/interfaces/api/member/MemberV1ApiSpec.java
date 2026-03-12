package com.loopers.interfaces.api.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import static com.loopers.interfaces.api.member.MemberV1Dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member V1 API", description = "회원 API 입니다.")
public interface MemberV1ApiSpec {

    @Operation(summary = "회원가입", description = "새로운 회원을 등록합니다.")
    ApiResponse<MemberResponse> signup(SignupRequest request);

    @Operation(summary = "내 정보 조회", description = "헤더 인증을 통해 내 정보를 조회합니다.")
    ApiResponse<MemberResponse> getMe(MemberModel member);

    @Operation(summary = "비밀번호 변경", description = "비밀번호를 변경합니다.")
    ApiResponse<Object> changePassword(MemberModel member, ChangePasswordRequest request);
}
