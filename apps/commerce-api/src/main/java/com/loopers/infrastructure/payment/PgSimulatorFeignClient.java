package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.ExternalPaymentClient;
import com.loopers.domain.payment.ExternalPaymentResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("!test")
@Slf4j
@RequiredArgsConstructor
@Component("pgSimulatorFeignClient")
public class PgSimulatorFeignClient implements ExternalPaymentClient {

    private final PgSimulatorClient pgSimulatorClient;

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pgRetry")
    @Override
    public void requestPayment(Long orderId, CardType cardType, String cardNo, long amount, String callbackUrl) {
        pgSimulatorClient.requestPayment(new PgSimulatorClient.PgPaymentRequest(
            String.valueOf(orderId),
            cardType.name(),
            cardNo,
            String.valueOf(amount),
            callbackUrl
        ));
    }

    @CircuitBreaker(name = "pgCircuit", fallbackMethod = "getPaymentByOrderIdFallback")
    @Override
    public ExternalPaymentResponse getPaymentByOrderId(Long orderId) {
        List<PgSimulatorClient.PgPaymentResponse> responses = pgSimulatorClient.getPaymentByOrderId(String.valueOf(orderId));
        if (responses == null || responses.isEmpty()) {
            return new ExternalPaymentResponse(null, false);
        }
        PgSimulatorClient.PgPaymentResponse response = responses.get(0);
        return new ExternalPaymentResponse(response.transactionId(), response.success());
    }

    void requestPaymentFallback(Long orderId, CardType cardType, String cardNo, long amount, String callbackUrl, Throwable t) {
        log.warn("PG 결제 요청 서킷브레이커 fallback. orderId={}, 이유={}", orderId, t.getMessage());
    }

    ExternalPaymentResponse getPaymentByOrderIdFallback(Long orderId, Throwable t) {
        log.warn("PG 상태 조회 서킷브레이커 fallback. orderId={}, 이유={}", orderId, t.getMessage());
        return new ExternalPaymentResponse(null, false);
    }
}