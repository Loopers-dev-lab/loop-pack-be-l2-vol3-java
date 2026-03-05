package com.loopers.interfaces.interceptor;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AdminAuthInterceptor.class);

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler){
        String adminString = request.getHeader(ADMIN_HEADER);

        if(adminString == null || adminString.isBlank()){
            log.warn("관리자 인증 헤더 누락 - URI: {}, RemoteAddr: {}", request.getRequestURI(), request.getRemoteAddr());
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증 헤더가 누락되었습니다.");
        }

        if(!adminString.equals(ADMIN_LDAP_VALUE)){
            log.warn("관리자 인증 실패 - URI: {}, RemoteAddr: {}", request.getRequestURI(), request.getRemoteAddr());
            throw new CoreException(ErrorType.UNAUTHORIZED, "관리자 인증에 실패했습니다.");
        }
        return true;
    }

}
