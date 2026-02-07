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

    public UserInfo signup(String loginId, String password, String name, String birthDate, String email) {
        User user = this.userService.signup(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String headerPassword, String currentPassword, String newPassword) {
        User user = this.userService.authenticate(loginId, headerPassword);
        this.userService.changePassword(user, currentPassword, newPassword);
    }
}
