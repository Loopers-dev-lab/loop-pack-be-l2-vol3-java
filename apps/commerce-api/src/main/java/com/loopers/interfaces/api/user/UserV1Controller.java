package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiHeaders;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserV1Controller implements UserApiV1Spec {

    private final UserFacade userFacade;

    @PostMapping
    @Override
    public ApiResponse<UserV1Dto.UserResponse> signUp(@RequestBody UserV1Dto.SignUpRequest request) {
        UserInfo info = userFacade.signUp(
                request.loginId(),
                request.password(),
                request.name(),
                request.birthDate(),
                request.email()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserV1Dto.UserResponse> getMyInfo(
            @RequestHeader(value = ApiHeaders.LOGIN_ID, required = false) String loginId,
            @RequestHeader(value = ApiHeaders.LOGIN_PW, required = false) String password
    ) {
        validateAuthHeaders(loginId, password);
        UserInfo info = userFacade.getMyInfo(loginId, password);
        return ApiResponse.success(UserV1Dto.UserResponse.from(info));
    }

    @PatchMapping("/me/password")
    @Override
    public ApiResponse<Object> changePassword(
            @RequestHeader(value = ApiHeaders.LOGIN_ID, required = false) String loginId,
            @RequestHeader(value = ApiHeaders.LOGIN_PW, required = false) String password,
            @RequestBody UserV1Dto.ChangePasswordRequest request
    ) {
        validateAuthHeaders(loginId, password);
        userFacade.changePassword(loginId, password, request.newPassword());
        return ApiResponse.success();
    }

    private void validateAuthHeaders(String loginId, String password) {
        if (loginId == null || loginId.isBlank() || password == null || password.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다");
        }
    }
}
