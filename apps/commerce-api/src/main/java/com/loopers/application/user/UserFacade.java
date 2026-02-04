package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserFacade {

    private final UserService userService;

    @Transactional
    public UserInfo signup(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        User user = userService.signup(loginId, rawPassword, name, birthDate, email);
        return UserInfo.from(user);
    }

    public UserInfo getMyInfo(User user) {
        return UserInfo.fromWithMaskedName(user);
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword) {
        userService.changePassword(user, currentPassword, newPassword);
    }
}
