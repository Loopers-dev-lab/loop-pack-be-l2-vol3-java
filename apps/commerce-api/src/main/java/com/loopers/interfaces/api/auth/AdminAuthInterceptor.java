package com.loopers.interfaces.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

// 어드민 인증은 이 프로젝트의 범위에서 고려하지 않으며, LDAP 헤더 값 고정 비교로 대체한다.
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String VALID_LDAP_VALUE = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ldap = request.getHeader(HEADER_LDAP);
        if (!VALID_LDAP_VALUE.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }
        return true;
    }
}
