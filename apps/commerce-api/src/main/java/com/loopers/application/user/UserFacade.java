package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public UserInfo signUp(SignupCommand command) {
        UserModel user = userService.signup(
            command.loginId(), command.password(), command.name(), command.birthday(), command.email()
        );
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(String loginId) {
        UserModel user = userService.findByLoginId(loginId);
        return UserInfo.from(user);
    }

    public void changePassword(ChangePasswordCommand command) {
        userService.changePassword(command.loginId(), command.currentPassword(), command.newPassword());
    }
}
