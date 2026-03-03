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
public class UserFacade {

    private final UserService userService;

    // Command

    @Transactional
    public UserInfo signUp(@Valid UserRequest.SignUp request) {
        UserCommand.SignUp command = UserCommand.SignUp.of(
                request.loginId(), request.password(), request.name(),
                request.birthDate(), request.email());
        User user = userService.signUp(command);
        return UserInfo.from(user);
    }

    @Transactional
    public void changePassword(Long id, @Valid UserRequest.ChangePassword request) {
        UserCommand.ChangePassword command = UserCommand.ChangePassword.of(id, request.newPassword());
        userService.changePassword(command);
    }

    // Query

    @Transactional(readOnly = true)
    public UserInfo getMyInfo(Long id) {
        User user = userService.getById(id);
        return UserInfo.from(user);
    }
}
