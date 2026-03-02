package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * 사용자 API 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserApiSpec {

    private final UserFacade userFacade;

    public UserController(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserResponse.SignupResponse> createUser(@RequestBody UserRequest.SignupRequest request) {
        UserInfo info = userFacade.createUser(
                request.loginId(), request.password(), request.name(), request.birthDate(), request.email()
        );
        return ApiResponse.success(UserResponse.SignupResponse.from(info));
    }

    @GetMapping("/me")
    @Override
    public ApiResponse<UserResponse.UserDetailResponse> getUser(@AuthUser User user) {
        return ApiResponse.success(UserResponse.UserDetailResponse.from(UserInfo.from(user)));
    }

    @PutMapping("/password")
    @Override
    public ApiResponse<Void> updateUserPassword(
            @AuthUser User user,
            @RequestBody UserRequest.ChangePasswordRequest request
    ) {
        userFacade.updateUserPassword(user, request.currentPassword(), request.newPassword());
        return ApiResponse.success(null);
    }
}
