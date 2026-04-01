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
 * 입장 토큰 검증 인터셉터. 주문 API 호출 시 토큰을 검증하고 원자적으로 소비한다.
 *
 * <p>실행 순서: CustomerAuthInterceptor (인증, userId 확보) → EntryTokenInterceptor (토큰 검증)</p>
 *
 * <p>Lua script(GET+비교+DEL)로 원자적 검증+삭제. 동시 요청 2건 중 1건만 통과한다.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EntryTokenInterceptor implements HandlerInterceptor {

    private final EntryTokenService entryTokenService;

    private static final String ENTRY_TOKEN_HEADER = "X-Entry-Token";

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // GET 요청은 토큰 불필요 (주문 목록 조회 등)
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        // 취소 API는 토큰 불필요 (/orders/{id}/cancel)
        if (request.getRequestURI().contains("/cancel")) {
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

        // 3. Lua 원자적 검증+삭제 (1회 사용 보장)
        boolean valid = entryTokenService.validateAndConsume(user.getUserId(), token);

        if (!valid) {
            log.info("토큰 검증 실패: userId={}, token={}", user.getUserId(), token);
            throw new CoreException(ErrorType.ENTRY_TOKEN_INVALID);
        }

        log.debug("토큰 검증 성공: userId={}", user.getUserId());
        return true;
    }
}
