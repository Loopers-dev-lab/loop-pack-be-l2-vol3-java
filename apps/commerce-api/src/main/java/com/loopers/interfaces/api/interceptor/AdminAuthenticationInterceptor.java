package com.loopers.interfaces.api.interceptor;

import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthenticationInterceptor implements HandlerInterceptor {

    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String ldap = request.getHeader("X-Loopers-Ldap");

        if (!ADMIN_LDAP_VALUE.equals(ldap)) {
            sendUnauthorizedResponse(response);
            return false;
        }
        return true;
    }

    private void sendUnauthorizedResponse(HttpServletResponse response) throws Exception {
        ErrorType errorType = ErrorType.UNAUTHORIZED;
        response.setStatus(errorType.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(
                "{\"code\":\"" + errorType.getCode() + "\",\"message\":\"" + errorType.getMessage() + "\"}"
        );
    }
}
