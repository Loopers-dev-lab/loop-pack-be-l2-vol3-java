package com.loopers.interfaces.api.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.queue.TokenService;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // POST 요청만 토큰 검증 (GET 요청은 토큰 불필요)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = request.getHeader(ENTRY_TOKEN_HEADER);
        String userIdStr = request.getHeader(USER_ID_HEADER);

        if (token == null || userIdStr == null) {
            log.warn("Entry token validation failed: missing headers. userId={}, token={}", userIdStr, token != null ? "[present]" : null);
            sendForbiddenResponse(response, "입장 토큰이 필요합니다.");
            return false;
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            log.warn("Invalid userId format: {}", userIdStr);
            sendForbiddenResponse(response, "유효하지 않은 사용자 ID입니다.");
            return false;
        }

        if (!tokenService.validateToken(userId, token)) {
            log.warn("Entry token validation failed: invalid token. userId={}", userId);
            sendForbiddenResponse(response, "유효하지 않은 토큰입니다. 대기열에서 순서를 기다려주세요.");
            return false;
        }

        return true;
    }

    private void sendForbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Object> apiResponse = ApiResponse.fail("FORBIDDEN", message);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
    }
}