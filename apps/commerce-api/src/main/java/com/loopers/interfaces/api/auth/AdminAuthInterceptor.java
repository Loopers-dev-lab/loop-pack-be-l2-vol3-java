package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(HEADER_LDAP);
        if (!ADMIN_LDAP.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "어드민 인증에 실패했습니다.");
        }
        return true;
    }
}
