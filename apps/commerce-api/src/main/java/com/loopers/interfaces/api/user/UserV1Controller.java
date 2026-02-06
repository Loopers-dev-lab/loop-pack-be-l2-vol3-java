package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.config.AuthInterceptor;
import com.loopers.domain.user.SignupCommand;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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
        SignupCommand command = new SignupCommand(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthday(),
            request.email()
        );
        UserInfo info = userFacade.signUp(command);
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMe(
        @RequestAttribute(AuthInterceptor.ATTR_LOGIN_ID) String loginId
    ) {
        UserInfo info = userFacade.getMyInfo(loginId);
        UserV1Dto.UserResponse response = UserV1Dto.UserResponse.from(info);
        return ApiResponse.success(response);
    }

    @PatchMapping("/password")
    @Override
    public ApiResponse<Object> changePassword(
        @RequestAttribute(AuthInterceptor.ATTR_LOGIN_ID) String loginId,
        @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(loginId, request.currentPassword(), request.newPassword());
        return ApiResponse.success();
    }
}
