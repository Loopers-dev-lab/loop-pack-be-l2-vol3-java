package com.loopers.interfaces.api;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.user.UserModel;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 입장 토큰 검증 인터셉터. 주문 API 호출 시 토큰을 검증하고, 주문 성공 후 소비한다.
 *
 * <p>실행 순서: CustomerAuthInterceptor (인증, userId 확보) → EntryTokenInterceptor (토큰 검증)</p>
 *
 * <p>preHandle에서 토큰 검증만 수행하고, afterCompletion에서 주문 성공 시에만 토큰을 삭제한다.
 * 주문 실패 시 토큰이 유지되어 TTL 내 재시도가 가능하다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private final EntryTokenService entryTokenService;

    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";
    private static final String TOKEN_VALIDATED_USER_ATTR = "entryTokenValidatedUserId";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        if (shouldSkip(request)) {
            return true;
        }

        // 1. 토큰 헤더 추출
        String token = request.getHeader(ENTRY_TOKEN_HEADER);
        if (token == null || token.isBlank()) {
            throw new CoreException(ErrorType.ENTRY_TOKEN_REQUIRED);
        }

        // 2. 인증된 유저 가져오기 (CustomerAuthInterceptor가 저장)
        UserModel user = (UserModel) request.getAttribute(CustomerAuthInterceptor.AUTH_USER_ATTR);
        if (user == null) {
            throw new CoreException(ErrorType.UNAUTHORIZED);
        }

        // 3. 검증만 수행 (삭제하지 않음) — 주문 실패 시 토큰 유지
        boolean valid = entryTokenService.validate(user.getUserId(), token);

        if (!valid) {
            log.info("토큰 검증 실패: userId={}", user.getUserId());
            throw new CoreException(ErrorType.ENTRY_TOKEN_INVALID);
        }

        // 후처리에서 userId를 참조할 수 있도록 저장
        request.setAttribute(TOKEN_VALIDATED_USER_ATTR, user.getUserId());
        log.debug("토큰 검증 성공 (삭제 대기): userId={}", user.getUserId());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {
        Long userId = (Long) request.getAttribute(TOKEN_VALIDATED_USER_ATTR);
        if (userId == null) {
            return;
        }

        if (ex == null && response.getStatus() < 400) {
            // 주문 성공 → 토큰 삭제
            entryTokenService.consume(userId);
            log.debug("토큰 소비 완료: userId={}", userId);
        } else {
            // 주문 실패 → 토큰 유지 (재시도 가능)
            log.info("주문 실패, 토큰 유지: userId={}, status={}, ex={}",
                    userId, response.getStatus(),
                    ex != null ? ex.getMessage() : "none");
        }
    }

    private boolean shouldSkip(HttpServletRequest request) {
        // GET 요청은 토큰 불필요 (주문 목록 조회 등)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 취소 API는 토큰 불필요: /api/v1/orders/{orderId}/cancel
        return request.getRequestURI().matches(".*/orders/\\d+/cancel$");
    }
}
