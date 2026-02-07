package com.loopers.application.auth;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class AuthFacade {
    private final UserService userService;

    public AuthFacade(UserService userService) {
        this.userService = userService;
    }

    public UserInfo createUser(String loginId, String password, String name, String birthDate, String email) {
        User user = this.userService.createUser(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    public void updateUserPassword(String loginId, String headerPassword, String currentPassword, String newPassword) {
        User user = this.userService.authenticateUser(loginId, headerPassword);
        this.userService.updateUserPassword(user, currentPassword, newPassword);
    }
}
