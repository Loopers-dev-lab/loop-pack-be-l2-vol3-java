package com.loopers.support.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 어드민 API 인증. /api-admin/** 경로에서 X-Loopers-Ldap 헤더 검증 (02 §0).
 * 헤더가 없거나 비어 있으면 401.
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String HEADER_LDAP = "X-Loopers-Ldap";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(HEADER_LDAP);
        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "어드민 인증이 필요합니다.");
        }
        return true;
    }
}
