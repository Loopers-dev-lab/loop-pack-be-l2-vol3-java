package com.loopers.application.user;

import com.loopers.domain.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFacade {

    private final UserService userService;

    // Command

    @Transactional
    public UserInfo signUp(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        User user = userService.signUp(loginId, rawPassword, name, birthDate, email);
        return UserInfo.from(user);
    }

    @Transactional
    public void changePassword(Long id, String newRawPassword) {
        userService.changePassword(id, newRawPassword);
    }

    // Query

    public UserInfo getMyInfo(Long id) {
        User user = userService.getById(id);
        return UserInfo.from(user);
    }
}
