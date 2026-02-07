package com.loopers.application.user;

import com.loopers.domain.user.User;
import org.springframework.stereotype.Component;

/**
 * 사용자 퍼사드 (Application Layer)
 *
 * 인증은 AuthUserResolver(@AuthUser)에서 완료된 상태이며,
 * Entity → DTO 변환만 담당한다.
 */
@Component
public class UserFacade {

    public UserInfo getUser(User user) {
        return UserInfo.from(user);
    }
}
