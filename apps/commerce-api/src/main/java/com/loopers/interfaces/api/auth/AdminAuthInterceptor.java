package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_LDAP = "X-Loopers-Ldap";

    private final String ldapCredential;

    public AdminAuthInterceptor(@Value("${auth.admin.ldap-credential}") String ldapCredential) {
        this.ldapCredential = ldapCredential;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String ldap = request.getHeader(HEADER_LDAP);

        if (ldap == null || ldap.isBlank()) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 필요합니다");
        }

        if (!ldapCredential.equals(ldap)) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증에 실패했습니다");
        }

        return true;
    }
}
