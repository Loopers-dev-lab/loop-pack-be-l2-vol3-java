package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller {

    private final UserService userService;

    @PostMapping
    public ApiResponse<UserV1Dto.UserResponse> signUp(
        @RequestBody UserV1Dto.SignUpRequest request
    ) {
        User user = userService.signUp(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthday(),
            request.email()
        );
        UserInfo info = UserInfo.from(user);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        UserInfo info = userService.getMyInfo(loginId, loginPw);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }
}
