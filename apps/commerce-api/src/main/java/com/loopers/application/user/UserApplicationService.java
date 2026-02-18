package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class UserApplicationService {

    private final UserDomainService userService;

    @Transactional
    public User signup(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        return userService.signup(loginId, rawPassword, name, birthDate, email);
    }

    @Transactional(readOnly = true)
    public User authenticate(String loginId, String rawPassword) {
        return userService.authenticate(loginId, rawPassword);
    }

    @Transactional
    public void changePassword(User user, String currentPassword, String newPassword) {
        userService.changePassword(user, currentPassword, newPassword);
    }
}
