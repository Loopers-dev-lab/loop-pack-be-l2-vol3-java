package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserV1Controller {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserV1Dto.RegisterResponse> register(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password,
        @Valid @RequestBody UserV1Dto.RegisterRequest request
    ) {
        User user = userService.register(
            loginId,
            password,
            request.name(),
            request.birthDate(),
            request.email(),
            request.gender()
        );

        UserV1Dto.RegisterResponse response = UserV1Dto.RegisterResponse.from(user);
        return ApiResponse.success(response);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserV1Dto.UserInfoResponse> getUserInfo(
        @PathVariable Long id
    ) {
        User user = userService.getUserInfo(id)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "사용자를 찾을 수 없습니다"));

        UserV1Dto.UserInfoResponse response = UserV1Dto.UserInfoResponse.from(user);
        return ApiResponse.success(response);
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> changePassword(
        @PathVariable Long id,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userService.changePassword(id, request.oldPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
