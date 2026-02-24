package com.loopers.interfaces.api.user;

import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.AuthUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserApplicationService userApplicationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> register(@Valid @RequestBody UserDto.RegisterRequest request) {
        userApplicationService.register(request.toCommand());
        return ApiResponse.success();
    }

    @GetMapping("/duplicate")
    public ApiResponse<UserDto.DuplicateCheckResponse> checkDuplicateLoginId(
            @RequestParam String loginId
    ) {
        boolean exists = userApplicationService.checkDuplicateLoginId(loginId);
        if (exists) {
            return ApiResponse.success(UserDto.DuplicateCheckResponse.unavailable(loginId));
        }
        return ApiResponse.success(UserDto.DuplicateCheckResponse.available(loginId));
    }

    @GetMapping("/me")
    public ApiResponse<UserDto.UserResponse> getMe(@AuthUser User user) {
        return ApiResponse.success(UserDto.UserResponse.from(user));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
            @AuthUser User user,
            @Valid @RequestBody UserDto.ChangePasswordRequest request
    ) {
        userApplicationService.changePassword(request.toCommand(user.id()));
        return ApiResponse.success();
    }
}
