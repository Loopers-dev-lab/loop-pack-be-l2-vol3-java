package com.loopers.application.payment;

import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * PG 호출부 전용.
 *
 * Phase 4: CircuitBreaker(Open/차단) 시 PG 미호출.
 * Phase 5: Retry(pgRetry) — 4xx(Feign client 예외)는 {@code ignore-exceptions}로 재시도하지 않음.
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
	@Retry(name = "pgRetry", fallbackMethod = "pgRetryFallback")
    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "pgCircuitFallback")
    public void requestPaymentToPg(PgSimulatorRequest request) {
        pgSimulatorClient.requestPayment(request);
    }

	/**
	 * Retry 소진 시 fallback (Phase 6).
	 * <p>
	 * 의도: facade 레벨에서는 PENDING 응답을 유지하도록 예외를 전파하지 않는다.
	 */
	@SuppressWarnings("unused")
	public void pgRetryFallback(PgSimulatorRequest request, Throwable throwable) {
		log.warn("PG 재시도 소진 후 fallback: orderId={}, reason={}",
				request.orderId(), throwable.toString());
		// Retry fallback에서 예외를 삼키면, 바깥의 CircuitBreaker가 실패로 집계하지 못한다.
		// 문서/테스트 요구사항: "fallbackMethod는 존재"하되 "CB 실패 집계"가 유지되어야 한다.
		if (throwable instanceof RuntimeException runtime) {
			throw runtime;
		}
		throw new RuntimeException(throwable);
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

