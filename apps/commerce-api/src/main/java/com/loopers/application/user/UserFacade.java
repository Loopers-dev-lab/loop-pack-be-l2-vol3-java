package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public UserInfo signup(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        UserModel user = userService.signup(loginId, rawPassword, name, birthDate, email);
        return UserInfo.from(user);
    }
}