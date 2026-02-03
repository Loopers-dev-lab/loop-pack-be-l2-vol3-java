package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberFacade memberFacade;

    @PostMapping
    @Override
    public ApiResponse<MemberV1Dto.RegisterResponse> register(@Valid @RequestBody MemberV1Dto.RegisterRequest request) {
        MemberInfo info = memberFacade.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(MemberV1Dto.RegisterResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberInfo info = memberFacade.getMyInfo(loginId, password);
        return ApiResponse.success(MemberV1Dto.MemberResponse.from(info));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Object> changePassword(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String currentPassword,
        @Valid @RequestBody MemberV1Dto.ChangePasswordRequest request
    ) {
        memberFacade.changePassword(loginId, currentPassword, request.newPassword());
        return ApiResponse.success();
    }
}
