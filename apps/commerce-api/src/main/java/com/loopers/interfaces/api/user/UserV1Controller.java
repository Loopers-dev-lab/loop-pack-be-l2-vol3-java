package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signup(@RequestBody UserV1Dto.SignupRequest request) {
        UserInfo info = userFacade.signup(
                request.loginId(), request.password(), request.name(), request.birthDate(), request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.fromSignup(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        UserInfo info = userFacade.getMyInfo(loginId, loginPw);
        return ApiResponse.success(UserV1Dto.UserResponse.fromMe(info));
    }

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<Void> changePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(loginId, loginPw, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
