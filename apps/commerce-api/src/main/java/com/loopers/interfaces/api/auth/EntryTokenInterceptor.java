package com.loopers.interfaces.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpMethod;
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
 * 토큰이 존재하면 {@link EntryTokenStore#validate}을 호출하여 검증한다.
 * 토큰 삭제는 주문 성공 후 이벤트 리스너에서 처리한다.
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
        if (!queueProperties.enabled() || !HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        String entryToken = request.getHeader(HEADER_ENTRY_TOKEN);
        if (entryToken == null) {
            throw new CoreException(ErrorType.INVALID_ENTRY_TOKEN);
        }

        Long userId = (Long) request.getAttribute(USER_ID_ATTRIBUTE);
        if (userId == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        entryTokenStore.validate(userId, entryToken);
        return true;
    }
}
