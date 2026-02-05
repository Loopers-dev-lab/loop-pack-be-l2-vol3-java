package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserAuthService;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserAuthService userAuthService;
    private final UserService userService;

    public void changePassword(UserFacadeDto.ChangePasswordRequest request) {
        User user = userAuthService.authenticate(request.toAuthenticateCommand());
        userService.changePassword(request.toChangePasswordCommand(user.birthDate()));
    }
}
