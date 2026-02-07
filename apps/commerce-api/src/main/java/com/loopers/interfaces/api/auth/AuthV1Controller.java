package com.loopers.interfaces.api.auth;

import com.loopers.application.auth.AuthFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 API 컨트롤러 (V1)
 *
 * 회원가입: 인증 불필요 (RequestBody만 사용)
 * 비밀번호 변경: @AuthUser로 헤더 인증 + RequestBody의 currentPassword로 2차 확인
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthV1Controller implements AuthV1ApiSpec {

    private final AuthFacade authFacade;

    public AuthV1Controller(AuthFacade authFacade) {
        this.authFacade = authFacade;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<AuthV1Response.SignupResponse> createUser(@RequestBody AuthV1Request.SignupRequest request) {
        UserInfo info = this.authFacade.createUser(
                request.loginId(), request.password(), request.name(), request.birthDate(), request.email()
        );
        return ApiResponse.success(AuthV1Response.SignupResponse.from(info));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> updateUserPassword(
            @AuthUser User user,
            @RequestBody AuthV1Request.ChangePasswordRequest request
    ) {
        this.authFacade.updateUserPassword(user, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
