package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.domain.member.MemberModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginMember;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberFacade memberFacade;

    @PostMapping
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> signup(
        @RequestBody MemberV1Dto.SignupRequest request
    ) {
        MemberInfo info = memberFacade.signup(
            request.loginId(), request.password(), request.name(),
            request.birthDate(), request.email()
        );
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> getMe(@LoginMember MemberModel member) {
        MemberInfo info = memberFacade.getMyInfo(member);
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Object> changePassword(
        @LoginMember MemberModel member,
        @RequestBody MemberV1Dto.ChangePasswordRequest request
    ) {
        memberFacade.changePassword(member, request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
