package com.loopers.infrastructure.pg.simulator;

import com.loopers.infrastructure.pg.*;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PG 시뮬레이터 구현체.
 *
 * <p>결제 요청(POST)에만 @CircuitBreaker 적용.
 * 상태 조회(GET)는 "복구 행위"이므로 CB 없이 Timeout + try-catch만으로 보호.</p>
 *
 * @see <a href="06-resilience-review.md §18">읽기 CB 제거 근거</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SimulatorPgClient implements PgClient {

    private static final String PROVIDER_NAME = "SIMULATOR";

    private final SimulatorFeignClient feignClient;

    @Override
    @CircuitBreaker(name = "pgSimulator-request")
    public PgPaymentResponse requestPayment(PgPaymentRequest request) {
        return feignClient.requestPayment(request);
    }

    /**
     * 상태 조회 — CB 없음. Timeout + try-catch로만 보호.
     * 복구 행위이므로 CB가 차단하면 복구가 멈춘다 (06 §18).
     */
    @Override
    public PgPaymentStatusResponse getPaymentStatus(String transactionKey) {
        try {
            return feignClient.getPaymentStatus(transactionKey);
        } catch (Exception e) {
            log.warn("PG 상태 확인 실패: transactionKey={}, error={}", transactionKey, e.getMessage());
            return new PgPaymentStatusResponse("UNKNOWN", transactionKey, null);
        }
    }

    @Override
    public PgPaymentStatusResponse getPaymentByOrderId(String orderId) {
        try {
            return feignClient.getPaymentByOrderId(orderId);
        } catch (Exception e) {
            log.warn("PG 주문별 조회 실패: orderId={}, error={}", orderId, e.getMessage());
            return new PgPaymentStatusResponse("UNKNOWN", null, null);
        }
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }
}
