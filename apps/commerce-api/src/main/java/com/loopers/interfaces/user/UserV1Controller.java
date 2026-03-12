package com.loopers.interfaces.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.dto.UserResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.Login;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.user.dto.UserV1Dto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserV1Controller implements UserV1ApiSpec {

    private final UserFacade userFacade;

    @PostMapping("/signup")
    @Override
    public ApiResponse<UserV1Dto.SignupResponse> signup(
        @Valid @RequestBody UserV1Dto.SignupRequest request
    ) {
        UserResult userInfo = userFacade.signup(request.toCriteria());

        return ApiResponse.success(UserV1Dto.SignupResponse.from(userInfo));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.MyInfoResponse> getMyInfo(@Login LoginUser loginUser) {
        UserResult userInfo = userFacade.getMyInfo(loginUser.loginId());

        return ApiResponse.success(UserV1Dto.MyInfoResponse.from(userInfo));
    }

    @PatchMapping("/password")
    @Override
    public ApiResponse<UserV1Dto.ChangePasswordResponse> changePassword(
        @Login LoginUser loginUser,
        @Valid @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        userFacade.changePassword(loginUser.loginId(), request.toCriteria());

        return ApiResponse.success(UserV1Dto.ChangePasswordResponse.success());
    }
}
