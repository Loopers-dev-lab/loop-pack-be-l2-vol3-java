package com.loopers.support.auth;

import com.loopers.application.user.UserFacade;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 고객 API 인증. 로그인이 필요한 경로에서만 적용 (02 §0, 01 §4.2).
 * X-Loopers-LoginId 헤더 검증 + 실존 사용자 여부 확인. 공백·미존재 loginId 시 401.
 */
@Component
public class CustomerAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";

    private final UserFacade userFacade;

    public CustomerAuthInterceptor(UserFacade userFacade) {
        this.userFacade = userFacade;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (userFacade.findUserIdByLoginId(loginId).isEmpty()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return true;
    }
}
