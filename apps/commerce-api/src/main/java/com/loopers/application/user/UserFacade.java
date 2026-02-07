package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import org.springframework.stereotype.Component;

@Component
public class UserFacade {
    private final UserService userService;

    public UserFacade(UserService userService) {
        this.userService = userService;
    }

    public UserInfo getUser(String loginId, String password) {
        User user = this.userService.authenticateUser(loginId, password);
        return UserInfo.from(user);
    }
}
