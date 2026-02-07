package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@Tag(name = "회원 관리 API", description = "회원 관련 API")
public interface MemberV1ApiSpec {
    @Operation(
            summary = "회원 등록",
            description = "새로운 회원을 등록합니다."
    )
    ApiResponse<MemberV1Dto.MemberResponse> register(
            @Schema(name = "회원 등록 요청 DTO", description = "회원 등록에 필요한 정보를 담고 있는 DTO")
            @Valid @RequestBody MemberV1Dto.RegisterRequest request
    );

    @Operation(
            summary = "내 정보 조회",
            description = "로그인한 회원의 정보를 조회합니다."
    )
    ApiResponse<MemberV1Dto.MeResponse> getMe(
            @Parameter(description = "로그인 ID") @RequestHeader("X-Loopers-LoginId") String loginId,
            @Parameter(description = "로그인 비밀번호") @RequestHeader("X-Loopers-LoginPw") String loginPw
    );

    @Operation(
            summary = "비밀번호 수정",
            description = "로그인한 회원의 비밀번호를 수정합니다."
    )
    ApiResponse<Void> changePassword(
            @Parameter(description = "로그인 ID") @RequestHeader("X-Loopers-LoginId") String loginId,
            @Parameter(description = "로그인 비밀번호") @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @Valid @RequestBody MemberV1Dto.ChangePasswordRequest request
    );
}
