package com.loopers.interfaces.api.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller {

    private final MemberService memberService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberV1Dto.SignUpResponse> signUp(@Valid @RequestBody MemberV1Dto.SignUpRequest request) {
        MemberModel member = memberService.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );

        MemberV1Dto.SignUpResponse response = new MemberV1Dto.SignUpResponse(
            member.getId(),
            member.getLoginId(),
            member.getName(),
            member.getEmail()
        );

        return ApiResponse.success(response);
    }
}
