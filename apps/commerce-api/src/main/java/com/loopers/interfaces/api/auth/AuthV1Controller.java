package com.loopers.interfaces.api.auth;

import com.loopers.application.auth.AuthFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 인증 API 컨트롤러 (V1)
 *
 * 인증 방식: 커스텀 헤더(X-Loopers-LoginId, X-Loopers-LoginPw) 기반 인증.
 * 비밀번호 변경 시 헤더 인증 + 본문 비밀번호 확인의 이중 검증을 수행한다.
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
    public ApiResponse<AuthV1Dto.SignupResponse> createUser(@RequestBody AuthV1Dto.SignupRequest request) {
        UserInfo info = this.authFacade.createUser(
                request.loginId(), request.password(), request.name(), request.birthDate(), request.email()
        );
        return ApiResponse.success(AuthV1Dto.SignupResponse.from(info));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> updateUserPassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @RequestBody AuthV1Dto.ChangePasswordRequest request
    ) {
        this.authFacade.updateUserPassword(loginId, loginPw, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
