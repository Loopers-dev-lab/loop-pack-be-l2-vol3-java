package com.loopers.config;

import com.loopers.application.queue.QueueTokenService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class QueueTokenInterceptor implements HandlerInterceptor {

    private final QueueTokenService queueTokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-User-Id 헤더가 필요합니다.");
        }

        String token = request.getHeader("X-Queue-Token");
        if (token == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-Queue-Token 헤더가 필요합니다.");
        }

        String eventId = request.getHeader("X-Event-Id");
        if (eventId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "X-Event-Id 헤더가 필요합니다.");
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            throw new CoreException(ErrorType.BAD_REQUEST, "X-User-Id 헤더가 올바른 숫자가 아닙니다.");
        }
        queueTokenService.validateToken(eventId, userId, token);
        return true;
    }
}
