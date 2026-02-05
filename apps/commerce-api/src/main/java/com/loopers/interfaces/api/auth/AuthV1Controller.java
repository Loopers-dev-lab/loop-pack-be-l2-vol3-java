package com.loopers.interfaces.api.auth;

import com.loopers.application.auth.AuthFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/auth")
public class AuthV1Controller implements AuthV1ApiSpec {

    private final AuthFacade authFacade;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<AuthV1Dto.SignupResponse> signup(@RequestBody AuthV1Dto.SignupRequest request) {
        UserInfo info = authFacade.signup(
                request.loginId(), request.password(), request.name(), request.birthDate(), request.email()
        );
        return ApiResponse.success(AuthV1Dto.SignupResponse.from(info));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> changePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @RequestBody AuthV1Dto.ChangePasswordRequest request
    ) {
        authFacade.changePassword(loginId, loginPw, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
