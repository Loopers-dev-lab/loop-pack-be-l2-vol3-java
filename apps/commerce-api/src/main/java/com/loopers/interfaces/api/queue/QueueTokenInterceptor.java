package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.TokenService;
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

    private static final String TOKEN_HEADER = "X-Queue-Token";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final TokenService tokenService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userId = request.getHeader(USER_ID_HEADER);
        String token = request.getHeader(TOKEN_HEADER);

        if (userId == null || userId.isBlank() || !tokenService.isValid(userId, token)) {
            throw new CoreException(ErrorType.QUEUE_TOKEN_INVALID);
        }
        return true;
    }
}