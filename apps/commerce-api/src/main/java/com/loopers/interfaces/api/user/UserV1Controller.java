package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 사용자 API 컨트롤러 (V1) */
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Response.UserResponse> getUser(@AuthUser User user) {
        return ApiResponse.success(UserV1Response.UserResponse.from(UserInfo.from(user)));
    }
}
