package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import com.loopers.interfaces.user.ChangePasswordRequest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo signUp(SignUpCommand command) {
        if (userService.existsByEmail(command.getEmail())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입되어 있는 아이디 입니다.");
        }

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
