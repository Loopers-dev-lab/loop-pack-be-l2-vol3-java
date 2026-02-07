package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.user.ChangePasswordRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(SignUpCommand command) {
        UserModel userModel = userService.createUser(
            command.getLoginId(),
            command.getLoginPw(),
            command.getBirthDate(),
            command.getName(),
            command.getEmail()
        );

        return UserInfo.from(userModel);
    }

    public UserInfo getMyInfo(Long userId) {
        UserModel user = userService.findById(userId);
        return UserInfo.from(user);
    }

    public void changePassword(Long userId, ChangePasswordRequest request) {
        userService.changePassword(userId, request.currentPassword(), request.newPassword());
    }
}
