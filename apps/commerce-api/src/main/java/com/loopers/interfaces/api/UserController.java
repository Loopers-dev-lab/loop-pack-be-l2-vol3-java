package com.loopers.interfaces.api;

import com.loopers.application.AuthenticationUseCase;
import com.loopers.application.PasswordUpdateUseCase;
import com.loopers.application.RegisterUseCase;
import com.loopers.application.UserQueryUseCase;
import com.loopers.domain.model.UserId;
import com.loopers.interfaces.api.dto.PasswordUpdateRequest;
import com.loopers.interfaces.api.dto.UserInfoResponse;
import com.loopers.interfaces.api.dto.UserRegisterRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final RegisterUseCase registerUseCase;
    private final UserQueryUseCase userQueryUseCase;
    private final PasswordUpdateUseCase passwordUpdateUseCase;
    private final AuthenticationUseCase authenticationUseCase;

    public UserController(
            RegisterUseCase registerUseCase,
            UserQueryUseCase userQueryUseCase,
            PasswordUpdateUseCase passwordUpdateUseCase,
            AuthenticationUseCase authenticationUseCase
    ) {
        this.registerUseCase = registerUseCase;
        this.userQueryUseCase = userQueryUseCase;
        this.passwordUpdateUseCase = passwordUpdateUseCase;
        this.authenticationUseCase = authenticationUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody UserRegisterRequest request) {
        registerUseCase.register(
                request.loginId(),
                request.name(),
                request.password(),
                request.birthday(),
                request.email()
        );
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getMyInfo(
            @NotBlank @RequestHeader("X-Loopers-LoginId") String loginId,
            @NotBlank @RequestHeader("X-Loopers-LoginPw") String loginPw
    ) {
        UserId userId = UserId.of(loginId);
        authenticationUseCase.authenticate(userId, loginPw);

        var userInfo = userQueryUseCase.getUserInfo(userId);
        return ResponseEntity.ok(UserInfoResponse.from(userInfo));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> updatePassword(
            @NotBlank @RequestHeader("X-Loopers-LoginId") String loginId,
            @NotBlank @RequestHeader("X-Loopers-LoginPw") String loginPw,
            @Valid @RequestBody PasswordUpdateRequest request
    ) {
        UserId userId = UserId.of(loginId);
        authenticationUseCase.authenticate(userId, loginPw);

        passwordUpdateUseCase.updatePassword(
                userId,
                request.currentPassword(),
                request.newPassword()
        );
        return ResponseEntity.ok().build();
    }
}
