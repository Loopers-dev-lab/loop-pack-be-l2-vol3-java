package com.loopers.interfaces.api.user;

import com.loopers.domain.user.NameMasker;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
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
    private final NameMasker nameMasker;

    @PostMapping("/register")
    public ApiResponse<UserV1Dto.UserResponse> register(
        @Valid @RequestBody UserV1Dto.RegisterRequest request
    ) {
        User user = userService.register(
            request.loginId(),
            request.password(),
            request.name(),
            request.email(),
            request.birthDate()
        );
        return ApiResponse.success(UserV1Dto.UserResponse.from(user));
    }

    @GetMapping("/me")
    public ApiResponse<UserV1Dto.MeResponse> getMe(
        @RequestHeader("X-Loopers-LoginId") String loginId,
        @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        User user = userService.authenticate(loginId, loginPw);
        return ApiResponse.success(new UserV1Dto.MeResponse(
            user.getLoginId(),
            nameMasker.mask(user.getName()),
            user.getEmail(),
            user.getBirthDate()
        ));
    }
}
