package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    public UserInfo register(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        User user = userService.register(loginId, rawPassword, name, birthDate, email);
        return UserInfo.from(user);
    }

    public UserInfo getMe(String loginId, String rawPassword) {
        User user = userService.authenticate(loginId, rawPassword);
        return UserInfo.from(user);
    }

    public void changePassword(String loginId, String currentRawPassword, String newRawPassword) {
        User user = userService.authenticate(loginId, currentRawPassword);
        userService.changePassword(user.getId(), currentRawPassword, newRawPassword);
    }
}
