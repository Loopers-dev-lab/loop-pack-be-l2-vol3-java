package com.loopers.infrastructure.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * {@code GET /api/v1/queue/position}, {@code GET /api/v1/queue/position/stream} 에 대해
 * {@code X-Loopers-LoginId} 기준 초당 요청 상한을 적용한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class QueuePositionRateLimitFilter extends OncePerRequestFilter {

    public static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";

    private final RedisQueuePositionRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;

    public QueuePositionRateLimitFilter(
            RedisQueuePositionRateLimiter rateLimiter,
            ObjectMapper objectMapper
    ) {
        this.rateLimiter = rateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String uri = request.getRequestURI();
        if ("/api/v1/queue/position".equals(uri)) {
            return false;
        }
        return !uri.startsWith("/api/v1/queue/position/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String loginId = request.getHeader(HEADER_LOGIN_ID);
        if (!rateLimiter.tryAcquire(loginId == null ? "" : loginId)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            ApiResponse<Object> body = ApiResponse.fail("TOO_MANY_REQUESTS", "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요.");
            objectMapper.writeValue(response.getOutputStream(), body);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
