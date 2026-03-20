package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.support.enums.CardType;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@link PaymentGateway} 구현체 — Resilience 정책을 적용하여 PG 호출을 보호한다.
 * <p>
 * Decorator 패턴으로 실행 순서를 코드에서 명시적으로 제어한다:
 * </p>
 * <pre>
 * Bulkhead (outermost)
 *   → CircuitBreaker
 *     → Retry (inner)
 *       → PgHttpClient.requestPayment() (실제 HTTP 호출)
 * </pre>
 * <p>
 * 이 순서는 yml의 aspect order가 아니라 코드에서 결정되므로,
 * 설정 실수로 Retry가 무력화되는 문제가 구조적으로 발생하지 않는다.
 * </p>
 *
 * @see PgHttpClient 순수 HTTP 통신 담당
 */
@Slf4j
@Component
public class ResilientPgClient implements PaymentGateway {

    private final PgHttpClient pgHttpClient;
    private final Bulkhead bulkhead;
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public ResilientPgClient(PgHttpClient pgHttpClient,
                              BulkheadRegistry bulkheadRegistry,
                              CircuitBreakerRegistry circuitBreakerRegistry,
                              RetryRegistry retryRegistry) {
        this.pgHttpClient = pgHttpClient;
        this.bulkhead = bulkheadRegistry.bulkhead("pgBulkhead");
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("pgCircuit");
        this.retry = retryRegistry.retry("pgRetry");
    }

    @Override
    public GatewayPaymentResult requestPayment(Long orderId, Long userId,
                                                CardType cardType, String cardNo,
                                                BigDecimal amount, String callbackUrl) {
        // ━━ Decorator 체인: Bulkhead(outer) → CB → Retry(inner) → 타임아웃 가드 → HTTP 호출 ━━
        Supplier<GatewayPaymentResult> supplier = () -> {
            try {
                return pgHttpClient.requestPayment(orderId, userId, cardType, cardNo, amount, callbackUrl);
            } catch (ResourceAccessException e) {
                if (e.getCause() instanceof SocketTimeoutException) {
                    // PG에 요청이 도달했을 수 있음 → retry하면 이중결제 위험 → 즉시 CoreException으로 변환
                    log.warn("타임아웃 가드: PG 타임아웃 감지, retry 차단. orderId={}", orderId);
                    throw new CoreException(ErrorType.PAYMENT_PG_TIMEOUT);
                }
                throw e; // ConnectException 등은 그대로 → Retry 대상
            }
        };

        Supplier<GatewayPaymentResult> withRetry = Retry.decorateSupplier(retry, supplier);
        Supplier<GatewayPaymentResult> withCb = CircuitBreaker.decorateSupplier(circuitBreaker, withRetry);
        Supplier<GatewayPaymentResult> withBulkhead = Bulkhead.decorateSupplier(bulkhead, withCb);

        try {
            return withBulkhead.get();
        } catch (BulkheadFullException e) {
            log.warn("Bulkhead FULL — PG 동시 호출 한도 초과. orderId={}", orderId);
            throw new CoreException(ErrorType.PAYMENT_SERVICE_UNAVAILABLE);
        } catch (CallNotPermittedException e) {
            log.warn("CircuitBreaker OPEN — PG 호출 차단. orderId={}", orderId);
            throw new CoreException(ErrorType.PAYMENT_SERVICE_UNAVAILABLE);
        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                log.warn("PG 타임아웃 — orderId={}, error={}", orderId, e.getMessage());
                throw new CoreException(ErrorType.PAYMENT_PG_TIMEOUT);
            }
            log.warn("PG 통신 에러 — orderId={}, error={}", orderId, e.getMessage());
            throw new CoreException(ErrorType.PAYMENT_PG_ERROR, e.getMessage());
        } catch (CoreException e) {
            throw e;
        } catch (Exception e) {
            log.warn("PG 에러 — orderId={}, error={}", orderId, e.getMessage());
            throw new CoreException(ErrorType.PAYMENT_PG_ERROR, e.getMessage());
        }
    }

    @Override
    public GatewayPaymentResult getPaymentStatus(String transactionKey) {
        return pgHttpClient.getPaymentStatus(transactionKey);
    }

    @Override
    public List<GatewayPaymentResult> getPaymentsByOrderId(Long orderId) {
        return pgHttpClient.getPaymentsByOrderId(orderId);
    }
}
