package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final UserFacade userFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserV1Dto.RegisterResponse> register(
        @Valid @RequestBody UserV1Dto.RegisterRequest request
    ) {
        UserInfo info = userFacade.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email()
        );
        return ApiResponse.success(UserV1Dto.RegisterResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMe(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password
    ) {
        UserInfo info = userFacade.getMe(loginId, password);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> changePassword(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(loginId, password, request.newPassword());
        return ApiResponse.success(null);
    }
}
