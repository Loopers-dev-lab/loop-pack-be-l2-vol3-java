package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentCommand;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgPaymentStatusResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgClientAdapter implements PgClient {
    private final PgFeignClient pgFeignClient;

    @Override
    @CircuitBreaker(name = "pg-client")
    @Retry(name = "pg-payment-request", fallbackMethod = "requestPaymentFallback")
    public PgPaymentResult requestPayment(PgPaymentCommand command) {
        PgFeignPaymentRequest request = new PgFeignPaymentRequest(
                String.format("ORD-%06d", command.orderId()),
                command.cardType(),
                command.cardNo(),
                String.valueOf(command.amount().longValue()),
                command.callbackUrl()
        );

        PgFeignPaymentResponse response = pgFeignClient.requestPayment(command.userId(), request);

        boolean accepted = !"UNKNOWN".equals(response.status());
        return new PgPaymentResult(accepted, response.transactionKey(), response.message());
    }

    private PgPaymentResult requestPaymentFallback(PgPaymentCommand command, Throwable t) {
        log.warn("PG 결제 요청 최종 실패, Fallback: orderId={}, error={}", command.orderId(), t.getMessage());
        return PgPaymentResult.fallback("PG 응답 지연 — 결제 확인 중: " + t.getMessage());
    }

    @Override
    @CircuitBreaker(name = "pg-client")
    @Retry(name = "pg-payment-status", fallbackMethod = "getPaymentStatusFallback")
    public PgPaymentStatusResult getPaymentStatus(Long orderId, Long userId) {
        PgFeignPaymentStatusResponse response = pgFeignClient.getPaymentStatus(userId, orderId);

        return new PgPaymentStatusResult(response.transactionKey(), response.status(), response.message());
    }

    private PgPaymentStatusResult getPaymentStatusFallback(Long orderId, Long userId, Throwable t) {
        log.warn("PG 상태 조회 최종 실패, Fallback: orderId={}, error={}", orderId, t.getMessage());
        return new PgPaymentStatusResult(null, "UNKNOWN", "PG 상태 조회 불가: " + t.getMessage());
    }
}
