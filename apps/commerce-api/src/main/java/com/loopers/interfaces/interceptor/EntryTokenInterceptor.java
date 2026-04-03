package com.loopers.interfaces.interceptor;

import com.loopers.application.user.UserInfo;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.interfaces.resolver.LoginUserArgumentResolver;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private final EntryTokenRepository entryTokenRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // GET 주문 조회 등 POST가 아닌 요청은 토큰 검증 불필요
        if (!"POST".equals(request.getMethod())) {
            return true;
        }

        // AuthInterceptor가 먼저 실행되어 ATTR_LOGIN_USER를 저장한 상태
        UserInfo userInfo = (UserInfo) request.getAttribute(LoginUserArgumentResolver.ATTR_LOGIN_USER);
        Long userId = userInfo.id();

        // 검증만. 삭제는 주문 성공 후 QueueOrderEventListener(AFTER_COMMIT)가 처리
        if (!entryTokenRepository.existsByUserId(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효한 입장 토큰이 없습니다.");
        }

        return true;
    }
}
