package com.loopers.application.payment;

import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PG 호출부 전용.
 *
 * Phase 4에서 CircuitBreaker(Open/차단) 상태일 때 실제 PG 호출이 발생하지 않도록,
 * {@link PgSimulatorClient#requestPayment(PgSimulatorRequest)} 자체를 CircuitBreaker로 감싼다.
 */
@Service
public class PgPaymentRequester {

    private static final Logger log = LoggerFactory.getLogger(PgPaymentRequester.class);

    private final PgSimulatorClient pgSimulatorClient;

    public PgPaymentRequester(PgSimulatorClient pgSimulatorClient) {
        this.pgSimulatorClient = pgSimulatorClient;
    }

    /**
     * CircuitBreaker(Open)일 때는 fallback으로 전환되어 pg 호출이 스킵된다.
     */
    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "pgCircuitFallback")
    public void requestPaymentToPg(PgSimulatorRequest request) {
        pgSimulatorClient.requestPayment(request);
    }

    /**
     * CircuitBreaker Open/실패 시 fallback. (Phase 4)
     * <p>
     * 의도: facade 레벨에서는 PENDING 응답을 유지하도록 예외를 전파하지 않는다.
     */
    @SuppressWarnings("unused")
    public void pgCircuitFallback(PgSimulatorRequest request, Throwable throwable) {
        log.warn("PG 호출 차단/실패로 fallback 동작: orderId={}, reason={}",
                request.orderId(), throwable.toString());
    }
}

