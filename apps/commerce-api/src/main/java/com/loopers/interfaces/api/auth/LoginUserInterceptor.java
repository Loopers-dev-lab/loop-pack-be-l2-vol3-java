package com.loopers.interfaces.api.auth;

import com.loopers.application.user.UserApplicationService;
import com.loopers.domain.user.User;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class LoginUserInterceptor implements HandlerInterceptor {

    private final UserApplicationService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader("X-Loopers-LoginId");
        String loginPw = request.getHeader("X-Loopers-LoginPw");

        if (loginId == null || loginPw == null) {
            throw new CoreException(ErrorType.NOT_FOUND, "인증 정보가 없습니다.");
        }

        User authenticated = userService.authenticate(loginId, loginPw);
        request.setAttribute("userId", authenticated.getId());
        return true;
    }
}
