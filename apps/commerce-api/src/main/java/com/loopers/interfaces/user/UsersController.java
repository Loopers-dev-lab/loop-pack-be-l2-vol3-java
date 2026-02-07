package com.loopers.interfaces.user;

import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.application.user.SignUpCommand;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import com.loopers.interfaces.api.CredentialsHeaders;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
@AllArgsConstructor
public class UsersController {

    private final UserFacade userFacade;

    @PostMapping
    public ApiResponse<UserDto.SignUpResponse> signUp(
        CredentialsHeaders credentialsHeaders,
        @Valid @RequestBody UsersSignUpRequestDto requestDto
    ) {
        SignUpCommand command = new SignUpCommand(
            credentialsHeaders.getLoginId(),
            credentialsHeaders.getLoginPw(),
            requestDto.getBirthDate(),
            requestDto.getName(),
            requestDto.getEmail()
        );

        UserInfo userInfo = userFacade.signUp(command);

        return ApiResponse.success(UserDto.SignUpResponse.from(userInfo));
    }

    @GetMapping("/me")
    public ApiResponse<UserDto.MyInfoResponse> getMe(@AuthUser AuthUserPrincipal authUser) {
        UserInfo userInfo = userFacade.getMyInfo(authUser.getId());
        return ApiResponse.success(UserDto.MyInfoResponse.from(userInfo));
    }

    @PatchMapping("/me/password")
    public ApiResponse<Void> changePassword(
        @AuthUser AuthUserPrincipal authUser,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        userFacade.changePassword(authUser.getId(), request);
        return ApiResponse.success(null);
    }
}
