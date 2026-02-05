package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberFacade memberFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<MemberV1Dto.SignUpResponse> signUp(
        @Valid @RequestBody MemberV1Dto.SignUpRequest request
    ) {
        LocalDate birthday = LocalDate.parse(request.birthday());
        MemberInfo info = memberFacade.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            birthday,
            request.email()
        );
        MemberV1Dto.SignUpResponse response = MemberV1Dto.SignUpResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<MemberV1Dto.MyInfoResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        MemberInfo info = memberFacade.getMyInfo(loginId, password);
        MemberV1Dto.MyInfoResponse response = MemberV1Dto.MyInfoResponse.from(info);
        return ApiResponse.success(response);
    }

}