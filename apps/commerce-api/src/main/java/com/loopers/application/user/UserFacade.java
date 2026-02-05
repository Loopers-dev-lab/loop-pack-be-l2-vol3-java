package com.loopers.application.user;

import com.loopers.domain.user.SignupCommand;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserFacade {
    private final UserService userService;

    public UserInfo signUp(SignupCommand command){
        UserModel user = userService.signup(command);
        return UserInfo.from(user);
    }
}
