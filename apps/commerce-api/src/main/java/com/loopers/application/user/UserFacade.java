package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;

    public UserInfo register(String loginId, String password, String name, LocalDate birthDate, String email) {
        User user = userService.register(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    public UserInfo getUserInfo(String loginId, String password) {
        User user = userService.getUserInfo(loginId, password);
        return UserInfo.from(user);
    }

    public void updatePassword(String loginId, String currentPassword, String newPassword, LocalDate birthDate) {
        userService.updatePassword(loginId, currentPassword, newPassword, birthDate);
    }
}
