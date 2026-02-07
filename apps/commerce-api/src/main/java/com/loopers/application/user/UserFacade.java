package com.loopers.application.user;

import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import org.springframework.stereotype.Component;

/**
 * 사용자 퍼사드 (Application Layer)
 *
 * 사용자 정보 조회 유스케이스를 오케스트레이션한다.
 * Entity를 외부에 노출하지 않고 UserInfo DTO로 변환하여 반환한다.
 */
@Component
public class UserFacade {
    private final UserService userService;

    public UserFacade(UserService userService) {
        this.userService = userService;
    }

    public UserInfo getUser(String loginId, String password) {
        User user = this.userService.authenticateUser(loginId, password);
        return UserInfo.from(user);
    }
}
