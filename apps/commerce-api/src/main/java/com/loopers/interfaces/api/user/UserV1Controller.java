package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import jakarta.validation.Valid;
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

    private final UserApplicationService userApplicationService;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.SignupResponse> signup(@Valid @RequestBody UserV1Dto.SignupRequest request) {
        User user = userApplicationService.signup(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(UserV1Dto.SignupResponse.from(user));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MeResponse> getMe(@AuthUser User user) {
        return ApiResponse.success(UserV1Dto.MeResponse.from(user));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> changePassword(@AuthUser User user, @Valid @RequestBody UserV1Dto.ChangePasswordRequest request) {
        userApplicationService.changePassword(user.getId(), request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
