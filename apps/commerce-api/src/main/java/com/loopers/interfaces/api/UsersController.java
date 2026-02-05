package com.loopers.interfaces.api;

import com.loopers.application.SignUpCommand;
import com.loopers.application.UserFacade;
import com.loopers.application.UserInfo;
import com.loopers.interfaces.UserDto;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
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
}
