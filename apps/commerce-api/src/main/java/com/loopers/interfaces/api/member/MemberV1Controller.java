package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
        Member member = memberService.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );

        MemberV1Dto.SignUpResponse response = new MemberV1Dto.SignUpResponse(
            member.getId(),
            member.getLoginId().value(),
            member.getName(),
            member.getEmail().value()
        );

        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<MemberV1Dto.MyInfoResponse> getMyInfo(@AuthMember Member member) {
        return ApiResponse.success(MemberV1Dto.MyInfoResponse.from(member));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Object> changePassword(
        @AuthMember Member member,
        @Valid @RequestBody MemberV1Dto.ChangePasswordRequest request
    ) {
        memberService.changePassword(member, request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
