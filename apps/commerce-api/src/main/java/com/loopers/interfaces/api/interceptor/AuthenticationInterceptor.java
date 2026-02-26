package com.loopers.interfaces.api.interceptor;

import com.loopers.application.user.AuthenticationUseCase;
import com.loopers.domain.model.user.UserId;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthenticationInterceptor implements HandlerInterceptor {

    private final AuthenticationUseCase authenticationUseCase;

    public AuthenticationInterceptor(AuthenticationUseCase authenticationUseCase) {
        this.authenticationUseCase = authenticationUseCase;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String loginId = request.getHeader("X-Loopers-LoginId");
        String loginPw = request.getHeader("X-Loopers-LoginPw");

        if (loginId == null || loginId.isBlank() || loginPw == null || loginPw.isBlank()) {
            sendUnauthorizedResponse(response);
            return false;
        }

        try {
            UserId userId = UserId.of(loginId);
            authenticationUseCase.authenticate(userId, loginPw);
            request.setAttribute("authenticatedUserId", userId);
            return true;
        } catch (IllegalArgumentException e) {
            sendUnauthorizedResponse(response);
            return false;
        }
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
