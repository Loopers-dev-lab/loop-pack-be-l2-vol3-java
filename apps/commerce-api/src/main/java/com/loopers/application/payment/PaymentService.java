package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PgClient;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentService {

    private static final String CALLBACK_URL = "http://localhost:8080/api/v1/payments/callback";

    private final PaymentRepository paymentRepository;
    private final PgClient pgClient;

    @Transactional
    @CircuitBreaker(name = "pg", fallbackMethod = "requestPaymentFallback")
    @Retry(name = "pg")
    public PaymentResult requestPayment(Long orderId, Long memberId, Long amount, String cardType, String cardNo) {
        Payment payment = paymentRepository.save(Payment.create(orderId, memberId, amount));

        PgClient.PgPaymentResponse pgResponse = pgClient.requestPayment(
            new PgClient.PgPaymentRequest(
                String.valueOf(orderId),
                cardType,
                cardNo,
                amount,
                CALLBACK_URL
            )
        );

        payment.assignPgTransactionKey(pgResponse.transactionKey());

        return new PaymentResult(payment.getId(), pgResponse.transactionKey(), payment.getStatus().name());
    }

    public PaymentResult requestPaymentFallback(Long orderId, Long memberId, Long amount, String cardType, String cardNo, Exception e) {
        log.warn("PG 요청 실패 - fallback 처리. orderId={}, error={}", orderId, e.getMessage());
        throw new CoreException(ErrorType.PAYMENT_CIRCUIT_OPEN, "현재 결제 시스템이 원활하지 않습니다. 잠시 후 다시 시도해주세요.");
    }

    @Transactional
    public void handleCallback(String pgTransactionKey, String status, String reason) {
        Payment payment = paymentRepository.findByPgTransactionKey(pgTransactionKey)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "결제 정보를 찾을 수 없습니다."));

        if ("SUCCESS".equals(status)) {
            payment.markSuccess();
        } else {
            payment.markFailed(reason);
        }
    }

    public record PaymentResult(Long paymentId, String pgTransactionKey, String status) {}
}
