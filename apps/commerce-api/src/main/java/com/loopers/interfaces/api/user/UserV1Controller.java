package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.application.user.UpdatePasswordCommand;
import com.loopers.application.user.UserApplicationService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.user.dto.UserV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserApplicationService userService;

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(@LoginUser Long userId) {
        UserInfo userInfo = userService.getMyInfo(userId);
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> updatePassword(
            @LoginUser Long userId,
            @RequestHeader("X-Loopers-LoginPw") String currentPassword,
            @Valid @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        userService.updatePassword(UpdatePasswordCommand.from(userId, currentPassword, request));
        return ApiResponse.success(null);
    }
}
