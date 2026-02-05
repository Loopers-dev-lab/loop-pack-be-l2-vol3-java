package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
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

    private final MemberFacade memberFacade;

    @PostMapping("/signup")
    public ApiResponse<MemberV1Dto.SignupResponse> signup(@RequestBody MemberV1Dto.SignupRequest request) {
        Member member = memberFacade.signup(request.toCommand());
        return ApiResponse.success(MemberV1Dto.SignupResponse.from(member));
    }

    @GetMapping("/me")
    public ApiResponse<MemberV1Dto.MeResponse> me(@LoginUser Member member) {
        return ApiResponse.success(MemberV1Dto.MeResponse.from(member));
    }

    @PutMapping("/me/password")
    public ApiResponse<Object> changePassword(
            @LoginUser Member member,
            @RequestBody MemberV1Dto.ChangePasswordRequest body
    ) {
        memberFacade.changePassword(member, body.currentPassword(), body.newPassword());
        return ApiResponse.success();
    }
}
