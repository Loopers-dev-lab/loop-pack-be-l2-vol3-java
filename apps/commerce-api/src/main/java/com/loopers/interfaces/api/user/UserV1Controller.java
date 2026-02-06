package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.UpdatePasswordCommand;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
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

    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(@RequestHeader("X-Loopers-LoginId") String loginId) {
        UserInfo userInfo = userService.getMyInfo(loginId);
        return ApiResponse.success(UserV1Dto.UserResponse.from(userInfo));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> updatePassword(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @Valid @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        userService.updatePassword(UpdatePasswordCommand.from(loginId, request));
        return ApiResponse.success(null);
    }
}