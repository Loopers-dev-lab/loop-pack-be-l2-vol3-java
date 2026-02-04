package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public UserInfo signup(String loginId, String password, String name, String birthDate, String email) {
        User user = userService.signup(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(String loginId, String password) {
        User user = userService.authenticate(loginId, password);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String headerPassword, String currentPassword, String newPassword) {
        User user = userService.authenticate(loginId, headerPassword);
        userService.changePassword(user, currentPassword, newPassword);
    }
}
