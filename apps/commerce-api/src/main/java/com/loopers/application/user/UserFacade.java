package com.loopers.application.user;

import com.loopers.domain.user.ChangePasswordCommand;
import com.loopers.domain.user.SignupCommand;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public UserInfo signUp(SignupCommand command){
        UserModel user = userService.signup(command);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(String loginId) {
        UserModel user = userService.findByLoginId(loginId);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String currentPassword, String newPassword) {
        ChangePasswordCommand command = new ChangePasswordCommand(loginId, currentPassword, newPassword);
        userService.changePassword(command);
    }
}
