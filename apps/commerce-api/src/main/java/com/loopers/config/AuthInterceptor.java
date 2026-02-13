package com.loopers.config;

import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiredArgsConstructor
@Component
public class AuthInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(AuthInterceptor.class);

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    public static final String ATTR_LOGIN_ID = "loginId";

    private final UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        String loginPw = request.getHeader(HEADER_LOGIN_PW);

        if (loginId == null || loginId.isBlank() || loginPw == null || loginPw.isBlank()) {
            log.warn("인증 헤더 누락 - URI: {}, RemoteAddr: {}", request.getRequestURI(), request.getRemoteAddr());
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 헤더가 누락되었습니다.");
        }

        try{
            userService.authenticate(loginId, loginPw);
        }catch(CoreException e){
            log.warn("인증 실패 - loginId: {}, URI: {}", loginId, request.getRequestURI());
            throw e;
        }
        request.setAttribute(ATTR_LOGIN_ID, loginId);

        return true;
    }
}
