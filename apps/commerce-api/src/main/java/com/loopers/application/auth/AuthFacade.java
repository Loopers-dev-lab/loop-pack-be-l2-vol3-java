package com.loopers.application.auth;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class AuthFacade {
    private final UserService userService;

    public UserInfo signup(String loginId, String password, String name, String birthDate, String email) {
        User user = userService.signup(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String headerPassword, String currentPassword, String newPassword) {
        User user = userService.authenticate(loginId, headerPassword);
        userService.changePassword(user, currentPassword, newPassword);
    }
}
