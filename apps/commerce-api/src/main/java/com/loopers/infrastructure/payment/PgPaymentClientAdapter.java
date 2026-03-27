package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.CardType.PgIdentifier;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PgOrderTransactions;
import com.loopers.domain.payment.PgOrderTransactions.PgTransactionSummary;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResult;
import com.loopers.domain.payment.PgRequestStatus;
import com.loopers.domain.payment.PgTransactionDetail;
import com.loopers.infrastructure.payment.dto.OrderTransactionsData;
import com.loopers.infrastructure.payment.dto.PGPaymentRequest;
import com.loopers.infrastructure.payment.dto.PaymentData;
import com.loopers.infrastructure.payment.dto.TransactionDetailData;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentClientAdapter implements PaymentGateway {

    private final PgPaymentFeignClient pg1Client;
    private final Pg2PaymentFeignClient pg2Client;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final MaintenanceWindowFilter maintenanceWindowFilter;

    @Override
    public PgPaymentResult requestPayment(PgPaymentRequest request) {
        maintenanceWindowFilter.checkMaintenanceWindow();

        CardType cardType = CardType.valueOf(request.cardType());

        // PG1 시도
        try {
            return executeWithCircuitBreakerAndRetry(
                    PgIdentifier.PG1, cardType,
                    () -> callPg1(request));
        } catch (CallNotPermittedException e) {
            log.warn("[PG1] {} 서킷 OPEN → PG2로 fallback", cardType.getDisplayName());
        } catch (Exception e) {
            log.warn("[PG1] {} 결제 실패 ({}) → PG2로 fallback",
                    cardType.getDisplayName(), e.getMessage());
        }

        // PG2 시도
        try {
            return executeWithCircuitBreakerAndRetry(
                    PgIdentifier.PG2, cardType,
                    () -> callPg2(request));
        } catch (CallNotPermittedException e) {
            log.error("[PG2] {} 서킷 OPEN → 결제 불가", cardType.getDisplayName());
        } catch (Exception e) {
            log.error("[PG2] {} 결제 실패 → 결제 불가 ({})",
                    cardType.getDisplayName(), e.getMessage());
        }

        return new PgPaymentResult(
                false, null, PgRequestStatus.CONNECTION_ERROR,
                cardType.getDisplayName() + " 결제 불가 - 모든 PG 실패");
    }

    @Override
    public PgTransactionDetail getPaymentStatus(String transactionKey, String userId) {
        TransactionDetailData data =
                pg1Client.getPaymentStatus(userId, transactionKey).data();

        return new PgTransactionDetail(
                data.transactionKey(),
                data.orderId(),
                data.cardType(),
                data.cardNo(),
                data.amount(),
                data.status(),
                data.reason());
    }

    @Override
    public PgOrderTransactions getPaymentsByOrder(String orderId, String userId) {
        OrderTransactionsData data =
                pg1Client.getPaymentsByOrder(userId, orderId).data();

        return new PgOrderTransactions(
                data.orderId(),
                data.transactions().stream()
                        .map(tx -> new PgTransactionSummary(
                                tx.transactionKey(), tx.status(), tx.reason()))
                        .toList());
    }

    // CB(outer) → Retry(inner) → Feign 순서로 실행
    // Retry 3회 실패 = CB에 1건의 실패로 기록
    private <T> T executeWithCircuitBreakerAndRetry(
            PgIdentifier pg, CardType cardType, Supplier<T> supplier) {
        String cbKey = cardType.circuitBreakerKey(pg);
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(cbKey);

        String retryName = pg.name().toLowerCase();
        Retry retry = retryRegistry.retry(retryName);

        Supplier<T> retryWrapped = Retry.decorateSupplier(retry, supplier);
        return circuitBreaker.executeSupplier(retryWrapped);
    }

    private PgPaymentResult callPg1(PgPaymentRequest request) {
        PgApiResponse<PaymentData> response =
                pg1Client.requestPayment(
                        request.userId(),
                        new PGPaymentRequest(
                                request.orderId(),
                                request.cardType(),
                                request.cardNo(),
                                request.amount(),
                                request.callbackUrl()));

        return toPgPaymentResult(response);
    }

    private PgPaymentResult callPg2(PgPaymentRequest request) {
        PgApiResponse<PaymentData> response =
                pg2Client.requestPayment(
                        request.userId(),
                        new PGPaymentRequest(
                                request.orderId(),
                                request.cardType(),
                                request.cardNo(),
                                request.amount(),
                                request.callbackUrl()));

        return toPgPaymentResult(response);
    }

    private PgPaymentResult toPgPaymentResult(
            PgApiResponse<PaymentData> response) {
        if (response.isSuccess()) {
            return new PgPaymentResult(
                    true, response.data().transactionKey(), PgRequestStatus.ACCEPTED);
        }
        return new PgPaymentResult(
                false, null, PgRequestStatus.VALIDATION_ERROR, response.meta().message());
    }
}
