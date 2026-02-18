package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 인증된 사용자의 식별 정보. 컨트롤러에서 "현재 사용자"를 식별하는 용도로 사용한다.
 */
@Getter
@AllArgsConstructor
public class AuthUserPrincipal {

    private final Long id;
    private final String loginId;

    public static AuthUserPrincipal from(UserModel user) {
        return new AuthUserPrincipal(
            user.getId(),
            user.getLoginId()
        );
    }
}
