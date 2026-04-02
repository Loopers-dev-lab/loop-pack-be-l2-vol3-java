package com.loopers.interfaces.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.queue.QueueService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueueTokenInterceptor implements HandlerInterceptor {

    private final QueueService queueService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String userIdHeader = request.getHeader("X-User-Id");
        String token = request.getHeader("X-Queue-Token");

        if (userIdHeader == null || token == null) {
            writeErrorResponse(response, "입장 토큰이 필요합니다.");
            return false;
        }

        try {
            Long userId = Long.valueOf(userIdHeader);
            queueService.validateToken(userId, token);
            return true;
        } catch (Exception e) {
            log.warn("토큰 검증 실패: userId={}, reason={}", userIdHeader, e.getMessage());
            writeErrorResponse(response, e.getMessage());
            return false;
        }
    }

    private void writeErrorResponse(HttpServletResponse response, String message) throws Exception {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Object> errorResponse = ApiResponse.fail("FORBIDDEN", message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
