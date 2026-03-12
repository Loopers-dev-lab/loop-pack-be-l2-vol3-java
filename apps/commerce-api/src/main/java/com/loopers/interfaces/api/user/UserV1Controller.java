package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserV1Dto.RegisterResponse> register(@RequestBody UserV1Dto.RegisterRequest request) {
        UserInfo info = userFacade.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(UserV1Dto.RegisterResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MeResponse> getMe(AuthenticatedUser authenticatedUser) {
        UserInfo info = userFacade.getMyInfo(
            authenticatedUser.loginId(),
            authenticatedUser.password()
        );
        return ApiResponse.success(UserV1Dto.MeResponse.from(info));
    }

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<UserV1Dto.ChangePasswordResponse> changePassword(
        AuthenticatedUser authenticatedUser,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(
            authenticatedUser.loginId(),
            request.currentPassword(),
            request.newPassword()
        );
        return ApiResponse.success(UserV1Dto.ChangePasswordResponse.success());
    }
}
