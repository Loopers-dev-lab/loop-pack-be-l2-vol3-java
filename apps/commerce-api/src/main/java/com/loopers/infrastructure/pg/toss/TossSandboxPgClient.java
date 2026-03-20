package com.loopers.infrastructure.pg.toss;

import com.loopers.infrastructure.pg.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Toss Sandbox PG 구현체 (동기 결제).
 *
 * <p>Toss는 동기 PG — confirm 호출 시 즉시 SUCCESS/FAILED 반환.
 * 콜백 대기가 불필요하며, requestPayment() 응답으로 최종 결과를 받는다.</p>
 *
 * <p>결제 요청(POST)에만 @CircuitBreaker 적용.
 * 상태 조회(GET)는 "복구 행위"이므로 CB 없이 Timeout + try-catch만으로 보호.</p>
 *
 * @see <a href="06-resilience-review.md §11.5">Toss 동기 결제</a>
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class TossSandboxPgClient implements PgClient {

    private static final String PROVIDER_NAME = "TOSS";

    private final TossFeignClient feignClient;

    @Override
    @CircuitBreaker(name = "pgToss-request")
    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        TossFeignClient.TossConfirmRequest tossRequest =
            new TossFeignClient.TossConfirmRequest(request.orderId(), request.amount());
        return feignClient.confirmPayment(tossRequest);
    }

    /**
     * 상태 조회 — CB 없음. Timeout + try-catch로만 보호.
     */
    @Override
    public PgPaymentStatusResponse getPaymentStatus(String transactionKey) {
        try {
            return feignClient.getPaymentStatus(transactionKey);
        } catch (Exception e) {
            log.warn("Toss 상태 확인 실패: transactionKey={}, error={}", transactionKey, e.getMessage());
            return new PgPaymentStatusResponse("UNKNOWN", transactionKey, null);
        }
    }

    @Override
    public PgPaymentStatusResponse getPaymentByOrderId(String orderId) {
        try {
            return feignClient.getPaymentByOrderId(orderId);
        } catch (Exception e) {
            log.warn("Toss 주문별 조회 실패: orderId={}, error={}", orderId, e.getMessage());
            return new PgPaymentStatusResponse("UNKNOWN", null, null);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
