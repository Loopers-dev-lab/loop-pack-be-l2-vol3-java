package com.loopers.application.auth;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import org.springframework.stereotype.Component;

/**
 * 인증 퍼사드 (Application Layer)
 *
 * 회원가입, 비밀번호 변경 등 인증 관련 유스케이스를 오케스트레이션한다.
 */
@Component
public class AuthFacade {
    private final UserService userService;

    public AuthFacade(UserService userService) {
        this.userService = userService;
    }

    public UserInfo createUser(String loginId, String password, String name, String birthDate, String email) {
        User user = this.userService.createUser(loginId, password, name, birthDate, email);
        return UserInfo.from(user);
    }

    /**
     * 비밀번호 변경
     *
     * 인증은 AuthUserResolver(@AuthUser)에서 완료된 상태이며,
     * 본문의 currentPassword로 2차 확인 후 변경한다.
     */
    public void updateUserPassword(User user, String currentPassword, String newPassword) {
        this.userService.updateUserPassword(user, currentPassword, newPassword);
    }
}
