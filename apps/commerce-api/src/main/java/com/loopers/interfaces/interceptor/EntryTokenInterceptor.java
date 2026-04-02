package com.loopers.interfaces.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.queue.TokenService;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private final TokenService tokenService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }
        Member member = (Member) request.getAttribute("authenticatedMember");
        if (member == null || !tokenService.validate(member.getId())) {
            sendUnauthorized(response);
            return false;
        }
        return true;
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        ErrorType errorType = ErrorType.TOKEN_INVALID;
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(
                objectMapper.writeValueAsString(
                        ApiResponse.fail(errorType.getCode(), errorType.getMessage())
                )
        );
    }
}
