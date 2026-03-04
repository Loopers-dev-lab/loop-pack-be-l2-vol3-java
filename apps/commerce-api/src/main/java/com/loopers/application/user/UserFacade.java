package com.loopers.application.user;

import com.loopers.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    // Command

    @Transactional
    public UserInfo signUp(UserCommand.SignUp command) {
        User user = userService.signUp(command);
        return UserInfo.from(user);
    }

    @Transactional
    public void changePassword(UserCommand.ChangePassword command) {
        userService.changePassword(command);
    }

    // Query

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(Long id) {
        User user = userService.getById(id);
        return UserInfo.from(user);
    }
}
