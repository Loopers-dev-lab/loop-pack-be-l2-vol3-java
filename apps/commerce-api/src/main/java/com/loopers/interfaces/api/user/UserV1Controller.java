package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserV1Controller {

    private final UserFacade userFacade;

    @PostMapping("/register")
    public ApiResponse<UserV1Dto.RegisterResponse> register(@RequestBody UserV1Dto.RegisterRequest request) {
        UserInfo userInfo = userFacade.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );

        UserV1Dto.RegisterResponse response = new UserV1Dto.RegisterResponse(
            userInfo.id(),
            userInfo.loginId(),
            userInfo.name(),
            userInfo.email()
        );

        return ApiResponse.success(response);
    }

    @GetMapping("/info")
    public ApiResponse<UserV1Dto.UserInfoResponse> getUserInfo(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String password
    ) {
        UserInfo userInfo = userFacade.getUserInfo(loginId, password);

        UserV1Dto.UserInfoResponse response = new UserV1Dto.UserInfoResponse(
            userInfo.loginId(),
            userInfo.getMaskedName(),
            userInfo.birthDate(),
            userInfo.email()
        );

        return ApiResponse.success(response);
    }

    @PutMapping("/password")
    public ApiResponse<Void> updatePassword(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String currentPassword,
        @RequestBody UserV1Dto.UpdatePasswordRequest request
    ) {
        UserInfo userInfo = userFacade.getUserInfo(loginId, currentPassword);
        userFacade.updatePassword(loginId, currentPassword, request.newPassword(), userInfo.birthDate());

        return ApiResponse.success(null);
    }
}
