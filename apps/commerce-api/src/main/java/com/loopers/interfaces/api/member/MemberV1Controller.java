package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberInfo;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller {

    private final MemberService memberService;

    @PostMapping
    public ApiResponse<MemberV1Dto.MemberResponse> register(
        @RequestBody MemberV1Dto.RegisterRequest request
    ) {
        Member member = memberService.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthday(),
            request.email()
        );
        MemberInfo info = MemberInfo.from(member);
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<MemberV1Dto.MemberResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        MemberInfo info = memberService.getMyInfo(loginId, loginPw);
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Object> changePassword(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw,
        @RequestBody MemberV1Dto.ChangePasswordRequest request
    ) {
        memberService.changePassword(loginId, loginPw, request.newPassword());
        return ApiResponse.success();
    }
}
