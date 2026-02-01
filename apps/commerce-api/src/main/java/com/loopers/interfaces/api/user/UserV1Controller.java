package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.SignupResponse> signup(@RequestBody UserV1Dto.SignupRequest request) {
        UserInfo info = userFacade.signup(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        UserV1Dto.SignupResponse response = UserV1Dto.SignupResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MeResponse> getMe(@AuthUser User user) {
        UserInfo info = userFacade.getMyInfo(user);
        UserV1Dto.MeResponse response = UserV1Dto.MeResponse.from(info);
        return ApiResponse.success(response);
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> changePassword(@AuthUser User user, @RequestBody UserV1Dto.ChangePasswordRequest request) {
        userFacade.changePassword(user, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
