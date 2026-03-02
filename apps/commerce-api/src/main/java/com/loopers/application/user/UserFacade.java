package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 Facade
 *
 * UserService를 위임하여 사용자 관련 유스케이스를 처리한다.
 */
@Component
public class UserFacade {

    private final UserService userService;

    public UserFacade(UserService userService) {
        this.userService = userService;
    }

    /** 회원가입 */
    @Transactional
    public UserInfo createUser(String loginId, String password, String name, String birthDate, String email) {
        User user = userService.createUser(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    /** 비밀번호 변경 */
    @Transactional
    public void updateUserPassword(User user, String currentPassword, String newPassword) {
        userService.updateUserPassword(user, currentPassword, newPassword);
    }
}
