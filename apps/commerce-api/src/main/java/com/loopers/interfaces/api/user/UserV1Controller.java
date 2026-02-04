package com.loopers.interfaces.api.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserV1Controller {

    private final UserService userService;

    @PostMapping("/register")
    public ApiResponse<UserV1Dto.RegisterResponse> register(
        @Valid @RequestBody UserV1Dto.RegisterRequest request
    ) {
        User user = userService.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.birthDate(),
            request.email(),
            request.gender()
        );

        UserV1Dto.RegisterResponse response = UserV1Dto.RegisterResponse.from(user);
        return ApiResponse.success(response);
    }
}
