package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public UserInfo getMyInfo(String loginId, String password) {
        User user = userService.authenticate(loginId, password);
        return UserInfo.from(user);
    }
}
