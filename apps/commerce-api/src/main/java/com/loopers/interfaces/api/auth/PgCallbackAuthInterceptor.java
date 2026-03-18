package com.loopers.interfaces.api.auth;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * PG 콜백 인증 인터셉터.
 * X-PG-Signature 헤더를 사전 공유된 비밀키와 비교한다 (shared secret header 검증, HMAC 아님).
 * 실제 운영에서는 HMAC 서명 검증이나 IP 화이트리스트로 대체할 수 있다.
 */
@Slf4j
@Component
public class PgCallbackAuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_PG_SIGNATURE = "X-PG-Signature";

    private final String callbackSecret;

    public PgCallbackAuthInterceptor(@Value("${pg.callback-secret}") String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String signature = request.getHeader(HEADER_PG_SIGNATURE);
        if (!callbackSecret.equals(signature)) {
            log.warn("[PG 콜백 인증 실패] uri={}, signaturePresent={}", request.getRequestURI(), signature != null);
            throw new CoreException(ErrorType.UNAUTHORIZED, "PG 콜백 인증에 실패했습니다.");
        }
        return true;
    }
}
