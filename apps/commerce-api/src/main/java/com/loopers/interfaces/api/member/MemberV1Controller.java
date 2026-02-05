package com.loopers.interfaces.api.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/members")
@RequiredArgsConstructor
public class MemberV1Controller {

    private final MemberService memberService;

    @PostMapping("/signup")
    public ApiResponse<MemberV1Dto.SignupResponse> signup(@RequestBody MemberV1Dto.SignupRequest request) {
        Member member = memberService.signup(request.toCommand());
        return ApiResponse.success(MemberV1Dto.SignupResponse.from(member));
    }

    @GetMapping("/me")
    public ApiResponse<MemberV1Dto.MeResponse> me(HttpServletRequest request) {
        Member member = (Member) request.getAttribute("authenticatedMember");
        return ApiResponse.success(MemberV1Dto.MeResponse.from(member));
    }

    @PutMapping("/me/password")
    public ApiResponse<Object> changePassword(
            HttpServletRequest request,
            @RequestBody MemberV1Dto.ChangePasswordRequest body
    ) {
        Member member = (Member) request.getAttribute("authenticatedMember");
        memberService.changePassword(member, body.currentPassword(), body.newPassword());
        return ApiResponse.success();
    }
}
