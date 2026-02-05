package com.loopers.interfaces.api.user;

import com.loopers.domain.user.LoginId;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import com.loopers.domain.user.UserInfo;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserApiSpec {

    private final UserService userService;

    @PostMapping
    @Override
    public ApiResponse<UserDto.SignupResponse> signup(@RequestBody UserDto.SignupRequest signRequest) {
        LoginId loginId = userService.signup(
                signRequest.loginId(),
                signRequest.password(),
                signRequest.name(),
                signRequest.birthDate(),
                signRequest.email()
        );

        UserDto.SignupResponse response = UserDto.SignupResponse.from(loginId);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    public ApiResponse<UserDto.MyInfoResponse> getMyInfo(
            @RequestHeader("X-Loopers-LoginId") String loginId,
            @RequestHeader("X-Loopers-LoginPw") String password) {
        UserInfo userInfo = userService.getMyInfo(loginId, password);
        return ApiResponse.success(UserDto.MyInfoResponse.from(userInfo));
    }
}
