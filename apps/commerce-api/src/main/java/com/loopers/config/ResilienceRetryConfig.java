package com.loopers.config;

import io.github.resilience4j.common.retry.configuration.RetryConfigCustomizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.function.Predicate;

/**
 * 결제 요청 전용 Retry 예외 필터.
 *
 * 결제 요청은 멱등성이 보장되지 않으므로, 재시도 대상을 엄격하게 제한한다:
 * - HttpServerErrorException (5xx): PG가 요청을 거부했음이 확실 → 재시도 안전
 * - ResourceAccessException 중 ConnectException 등: PG에 요청이 도달하지 않음 → 재시도 안전
 * - ResourceAccessException 중 SocketTimeoutException: PG에 요청이 도달했을 수 있음 → 재시도 금지 (중복 결제 위험)
 *
 * YAML의 retry-exceptions는 예외 클래스 레벨만 지정 가능하여 cause 구분이 불가능하므로,
 * pgPaymentRequest 인스턴스는 이 커스텀 Predicate로 retry 대상을 결정한다.
 */
@Slf4j
@Configuration
public class ResilienceRetryConfig {

    @SuppressWarnings("unchecked")
    @Bean
    public RetryConfigCustomizer pgPaymentRequestRetryConfigCustomizer() {
        return RetryConfigCustomizer.of("pgPaymentRequest", builder ->
                builder.retryOnException(pgPaymentRetryPredicate())
        );
    }

    private Predicate<Throwable> pgPaymentRetryPredicate() {
        return t -> {
            // PG 5xx 에러: 요청이 처리되지 않았음이 확실 → 재시도
            if (t instanceof HttpServerErrorException) {
                return true;
            }
            // I/O 에러: cause에 따라 판단
            if (t instanceof ResourceAccessException) {
                if (t.getCause() instanceof SocketTimeoutException) {
                    // 읽기 타임아웃: PG가 요청을 수신했을 수 있음 → 재시도 금지
                    log.warn("[Retry] SocketTimeoutException 감지 — 재시도 금지 (중복 결제 방지): {}",
                            t.getMessage());
                    return false;
                }
                // ConnectException 등: PG에 도달하지 않음 → 재시도
                return true;
            }
            return false;
        };
    }
}
