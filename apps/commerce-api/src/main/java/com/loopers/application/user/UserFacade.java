package com.loopers.application.user;

import org.springframework.stereotype.Component;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserResult signUp(String loginId, String password, String name, String birthDate, String email) {
        User user = userService.signUp(loginId, password, name, birthDate, email);
        return UserResult.from(user);
    }
}
