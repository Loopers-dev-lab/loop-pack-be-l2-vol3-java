package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PgPaymentGateway implements PaymentGateway {

    private static final String ORDER_ID_FORMAT = "ORD-%06d";

    private final PgPaymentClient pgPaymentClient;

    @Override
    @Retry(name = "pgPayment")
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "requestPaymentFallback")
    public PaymentGatewayResponse requestPayment(String userId, PaymentGatewayRequest request) {
        String pgOrderId = formatOrderId(request.orderId());
        PgPaymentRequest pgRequest = new PgPaymentRequest(
                pgOrderId, request.cardType(), request.cardNo(), request.amount(), request.callbackUrl()
        );
        PgPaymentResponse<PgPaymentResponse.TransactionResponse> response =
                pgPaymentClient.requestPayment(userId, pgRequest);
        validateResponse(response);
        return new PaymentGatewayResponse(
                response.data().transactionKey(), response.data().status(), response.data().reason()
        );
    }

    @Override
    @Retry(name = "pgPayment")
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "getTransactionFallback")
    public PaymentGatewayDetailResponse getTransaction(String userId, String transactionKey) {
        PgPaymentResponse<PgPaymentResponse.TransactionDetailResponse> response =
                pgPaymentClient.getTransaction(userId, transactionKey);
        validateResponse(response);
        var data = response.data();
        return new PaymentGatewayDetailResponse(
                data.transactionKey(), data.orderId(), data.cardType(), data.cardNo(),
                data.amount(), data.status(), data.reason()
        );
    }

    @Override
    @Retry(name = "pgPayment")
    @CircuitBreaker(name = "pgPayment", fallbackMethod = "getTransactionsByOrderFallback")
    public PaymentGatewayOrderResponse getTransactionsByOrder(String userId, String orderId) {
        String pgOrderId = formatOrderId(orderId);
        PgPaymentResponse<PgPaymentResponse.OrderResponse> response =
                pgPaymentClient.getTransactionsByOrder(userId, pgOrderId);
        validateResponse(response);
        var transactions = response.data().transactions().stream()
                .map(t -> new PaymentGatewayResponse(t.transactionKey(), t.status(), t.reason()))
                .toList();
        return new PaymentGatewayOrderResponse(response.data().orderId(), transactions);
    }

    private PaymentGatewayResponse requestPaymentFallback(String userId, PaymentGatewayRequest request, Exception e) {
        log.warn("PG 결제 요청 fallback 실행: orderId={}, error={}", request.orderId(), e.getMessage());
        return new PaymentGatewayResponse(null, "PENDING", "PG 서비스 불안정으로 결제 처리 대기 중");
    }

    private PaymentGatewayDetailResponse getTransactionFallback(String userId, String transactionKey, Exception e) {
        log.warn("PG 거래 조회 fallback 실행: transactionKey={}, error={}", transactionKey, e.getMessage());
        throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 서비스가 불안정합니다. 잠시 후 다시 시도해주세요.");
    }

    private PaymentGatewayOrderResponse getTransactionsByOrderFallback(String userId, String orderId, Exception e) {
        log.warn("PG 주문 조회 fallback 실행: orderId={}, error={}", orderId, e.getMessage());
        throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 서비스가 불안정합니다. 잠시 후 다시 시도해주세요.");
    }

    private String formatOrderId(String orderId) {
        try {
            return String.format(ORDER_ID_FORMAT, Long.parseLong(orderId));
        } catch (NumberFormatException e) {
            return orderId;
        }
    }

    private <T> void validateResponse(PgPaymentResponse<T> response) {
        if (!response.isSuccess()) {
            String errorMessage = response.meta() != null ? response.meta().message() : "PG 응답 실패";
            log.error("PG 응답 실패: {}", errorMessage);
            throw new CoreException(ErrorType.INTERNAL_ERROR, "PG 결제 처리 실패: " + errorMessage);
        }
    }
}
