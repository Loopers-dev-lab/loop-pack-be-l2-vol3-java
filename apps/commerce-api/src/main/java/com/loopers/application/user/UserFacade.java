package com.loopers.application.user;

import com.loopers.domain.user.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserFacade {

    private final UserService userService;

    // Command

    @Transactional
    public UserInfo signUp(@Valid UserRequest.SignUp request) {
        User user = userService.signUp(
                request.loginId(), request.password(), request.name(),
                request.birthDate(), request.email());
        return UserInfo.from(user);
    }

    @Transactional
    public void changePassword(Long id, @Valid UserRequest.ChangePassword request) {
        userService.changePassword(id, request.newPassword());
    }

    // Query

    public UserInfo getMyInfo(Long id) {
        User user = userService.getById(id);
        return UserInfo.from(user);
    }
}
