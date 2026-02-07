package com.loopers.application.auth;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import org.springframework.stereotype.Component;

/**
 * 인증 퍼사드 (Application Layer)
 *
 * 회원가입, 비밀번호 변경 등 인증 관련 유스케이스를 오케스트레이션한다.
 * Entity를 외부에 노출하지 않고 UserInfo DTO로 변환하여 반환한다.
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
     * 비밀번호 변경 시 이중 인증을 수행한다.
     * 1단계: 헤더 credentials(loginId + headerPassword)로 사용자 인증
     * 2단계: 요청 본문의 currentPassword로 비밀번호 확인 후 변경
     */
    public void updateUserPassword(String loginId, String headerPassword, String currentPassword, String newPassword) {
        User user = this.userService.authenticateUser(loginId, headerPassword);
        this.userService.updateUserPassword(user, currentPassword, newPassword);
    }
}
