package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.member.dto.AddMemberApiReqDto;
import com.loopers.interfaces.api.member.dto.FindMemberApiResDto;
import com.loopers.interfaces.api.member.dto.PutMemberPasswordApiReqDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Member V1 API", description = "회원 관리 API 입니다.")
public interface MemberV1ApiSpec {

    @Operation(
        summary = "회원가입",
        description = "새로운 회원을 등록합니다."
    )
    ApiResponse<Void> addMember(
        AddMemberApiReqDto request
    );

    @Operation(
        summary = "내 정보 조회",
        description = "헤더 인증 정보로 본인의 정보를 조회합니다."
    )
    ApiResponse<FindMemberApiResDto> findMember(
        @Parameter(description = "로그인 ID", required = true)
        String loginId,
        @Parameter(description = "비밀번호", required = true)
        String password
    );

    @Operation(
        summary = "비밀번호 변경",
        description = "헤더 인증 후 비밀번호를 변경합니다."
    )
    ApiResponse<Void> putPassword(
        @Parameter(description = "로그인 ID", required = true)
        String loginId,
        @Parameter(description = "비밀번호", required = true)
        String password,
        PutMemberPasswordApiReqDto request
    );
}
