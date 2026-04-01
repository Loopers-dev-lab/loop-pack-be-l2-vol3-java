package com.loopers.interfaces.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.loopers.support.entry.EntryTokenStore;

import lombok.RequiredArgsConstructor;

/**
 * 주문 생성 시 입장 토큰을 검증하는 인터셉터.
 *
 * <p>요청 헤더 {@code X-Entry-Token}에 토큰이 존재하면
 * {@link EntryTokenStore#validateAndConsume}을 호출하여 검증 후 소멸시킨다.
 * 토큰 헤더가 없으면 검증을 건너뛴다.</p>
 */
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String HEADER_ENTRY_TOKEN = "X-Entry-Token";
    private static final String USER_ID_ATTRIBUTE = "userId";

    private final EntryTokenStore entryTokenStore;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String entryToken = request.getHeader(HEADER_ENTRY_TOKEN);
        if (entryToken == null) {
            return true;
        }

        Long userId = (Long) request.getAttribute(USER_ID_ATTRIBUTE);
        entryTokenStore.validateAndConsume(userId, entryToken);
        return true;
    }
}
