package com.loopers.interfaces.api.member;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Member V1 API", description = "회원 API")
@RequestMapping("/api/v1/members")
public interface MemberV1ApiSpec {

    @Operation(summary = "회원가입", description = "새 회원을 등록합니다.")
    ApiResponse<MemberV1Dto.SignUpResponse> signUp(@Valid @RequestBody MemberV1Dto.SignUpRequest request);
}
