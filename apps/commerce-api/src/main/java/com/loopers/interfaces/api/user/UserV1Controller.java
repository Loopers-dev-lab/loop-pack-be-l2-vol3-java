package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping("/signup")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signup(
        @RequestBody UserV1Dto.SignupRequest request
    ) {
        UserInfo info = userFacade.signUp(request.toCommand());
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMe(
        @LoginUser UserInfo userInfo
    ) {
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(userInfo);
        return ApiResponse.success(response);
    }

    @PatchMapping("/password")
    @Override
    public ApiResponse<Object> changePassword(
        @LoginUser UserInfo userInfo,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(request.toCommand(userInfo.loginId()));
        return ApiResponse.success();
    }
}
