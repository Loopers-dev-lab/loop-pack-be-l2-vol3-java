package com.loopers.interfaces.api.member;

import com.loopers.application.member.MemberFacade;
import com.loopers.application.member.MemberInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberFacade memberFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<MemberV1Dto.SignUpResponse> signUp(
        @RequestBody MemberV1Dto.SignUpRequest request
    ) {
        LocalDate birthday = parseBirthday(request.birthday());
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

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<Object> updatePassword(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @RequestBody MemberV1Dto.UpdatePasswordRequest request
    ) {
        if (!password.equals(request.currentPassword())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "인증 정보가 일치하지 않습니다.");
        }
        memberFacade.updatePassword(loginId, request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }

    private LocalDate parseBirthday(String birthday) {
        if (birthday == null || birthday.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일은 비어있을 수 없습니다.");
        }
        try {
            return LocalDate.parse(birthday);
        } catch (DateTimeParseException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "생년월일 형식이 올바르지 않습니다. (yyyy-MM-dd)");
        }
    }
}