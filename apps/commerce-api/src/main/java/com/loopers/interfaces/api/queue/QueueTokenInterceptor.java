package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueTokenValidator;
import com.loopers.domain.queue.QueueProperties;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.QueueErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 대기열 토큰 검증 Interceptor
 *
 * 주문 API 경로(/api/v1/orders)에 대해 대기열 토큰을 검증한다.
 * 대기열이 꺼져 있으면 토큰 검증을 건너뛴다 (Feature Flag).
 *
 * 설계 결정:
 * - Interceptor를 선택한 이유: 주문 도메인이 큐의 존재를 모르게 하기 위해
 * - 인터셉터는 "통과/거절"만 판단 — 서킷 브레이커, 폴백 등 비즈니스 판단은
 *   QueueTokenValidator(Application Layer)에 위임
 *
 * Redis 장애 대응:
 * - QueueTokenValidator 내부의 @CircuitBreaker + RateLimiter Fallback이 처리
 * - Fail-Open: 이커머스는 가용성(매출) 우선 — 완전 차단보다 제한적 허용
 */
@Component
public class QueueTokenInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(QueueTokenInterceptor.class);

    private final QueueTokenValidator queueTokenValidator;
    private final QueueProperties queueProperties;

    public QueueTokenInterceptor(QueueTokenValidator queueTokenValidator,
                                  QueueProperties queueProperties) {
        this.queueTokenValidator = queueTokenValidator;
        this.queueProperties = queueProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!queueProperties.isEnabled()) {
            return true;
        }

        Long userId = extractUserId(request);
        if (userId == null) {
            return true;
        }

        boolean hasToken = queueTokenValidator.validateToken(userId);
        if (!hasToken) {
            log.warn("대기열 토큰 없음: userId={}, path={}", userId, request.getRequestURI());
            throw new CoreException(QueueErrorType.QUEUE_TOKEN_REQUIRED);
        }
        return true;
    }

    private Long extractUserId(HttpServletRequest request) {
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
