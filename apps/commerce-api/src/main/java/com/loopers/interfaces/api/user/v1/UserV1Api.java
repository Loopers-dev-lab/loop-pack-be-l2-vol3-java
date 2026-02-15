package com.loopers.interfaces.api.user.v1;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.user.UserResult;
import com.loopers.application.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Api implements UserV1ApiSpec {

    private final UserService userService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserV1Dto.SignUpResponse> signUp(@Valid @RequestBody UserV1Dto.SignUpRequest request) {
        UserResult userResult = userService.signUp(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );

        return ApiResponse.success(UserV1Dto.SignUpResponse.from(userResult));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MeResponse> getMyInfo(@LoginUser Long userId) {
        UserResult userResult = userService.getMyInfo(userId);
        return ApiResponse.success(UserV1Dto.MeResponse.from(userResult));
    }

    @PutMapping("/me/password")
    @Override
    public ApiResponse<Object> updatePassword(
            @LoginUser Long userId,
            @Valid @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        userService.updatePassword(userId, request.oldPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
