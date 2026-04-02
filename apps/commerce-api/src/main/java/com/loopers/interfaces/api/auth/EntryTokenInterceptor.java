package com.loopers.interfaces.api.auth;

import com.loopers.domain.queue.EntryTokenConsumeResult;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@RequiredArgsConstructor
@Component
public class EntryTokenInterceptor implements HandlerInterceptor {

    public static final String ATTRIBUTE_AUTH_USER = "authenticatedUser";
    public static final String ATTRIBUTE_ENTRY_TOKEN = "entryToken";

    private final EntryTokenRepository entryTokenRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        AuthenticatedUser authUser = (AuthenticatedUser) request.getAttribute(ATTRIBUTE_AUTH_USER);
        if (authUser == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED, "인증 정보가 없습니다.");
        }

        EntryTokenConsumeResult result = entryTokenRepository.consumeIfActivated(authUser.userId(), System.currentTimeMillis())
            .orElseThrow(() -> new CoreException(ErrorType.BAD_REQUEST, "입장 토큰이 없습니다."));

        if (!result.consumed()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "아직 입장 시간이 아닙니다.");
        }

        request.setAttribute(ATTRIBUTE_ENTRY_TOKEN, result.token());
        return true;
    }
}
