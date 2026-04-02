package com.loopers.interfaces.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.loopers.support.entry.EntryTokenStore;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.queue.QueueProperties;

import lombok.RequiredArgsConstructor;

/**
 * 주문 생성 시 입장 토큰을 검증하는 인터셉터.
 *
 * <p>대기열이 활성 상태({@code queue.enabled=true})이면 {@code X-Entry-Token} 헤더가 필수다.
 * 토큰이 존재하면 {@link EntryTokenStore#validateAndConsume}을 호출하여 검증 후 소멸시킨다.
 * 대기열이 비활성 상태이면 토큰 없이도 통과시킨다.</p>
 */
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private static final String HEADER_ENTRY_TOKEN = "X-Entry-Token";
    private static final String USER_ID_ATTRIBUTE = "userId";

    private final EntryTokenStore entryTokenStore;
    private final QueueProperties queueProperties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String entryToken = request.getHeader(HEADER_ENTRY_TOKEN);
        if (entryToken == null) {
            if (queueProperties.enabled()) {
                throw new CoreException(ErrorType.INVALID_ENTRY_TOKEN);
            }
            return true;
        }

        Long userId = (Long) request.getAttribute(USER_ID_ATTRIBUTE);
        entryTokenStore.validateAndConsume(userId, entryToken);
        return true;
    }
}
