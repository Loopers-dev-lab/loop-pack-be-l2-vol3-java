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

    // 어노테이션 순서가 곧 레이어 순서다.
    // 요청 → [CircuitBreaker] → [Retry] → PgClient
    //
    // 반드시 CircuitBreaker가 Retry를 감싸야 한다.
    // Retry가 바깥에 있으면 재시도가 CircuitBreaker의 실패 카운트를 증폭시켜
    // Thundering Herd 문제를 유발한다.
    // (3회 재시도 * N개 요청 = 실패 카운트 3N배 증폭 → OPEN 가속)
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

    // Fallback 설계 원칙: Fail Gracefully
    // 단순 500을 내리면 클라이언트가 원인을 알 수 없어 무한 재시도한다.
    // PAYMENT_CIRCUIT_OPEN 에러 코드로 "재시도 불가 + 시스템 점검 중" 상태를 명시적으로 전달한다.
    public PaymentResult requestPaymentFallback(Long orderId, Long memberId, Long amount, String cardType, String cardNo, Exception e) {
        log.warn("PG 요청 실패 - fallback 처리. orderId={}, error={}", orderId, e.getMessage());
        throw new CoreException(ErrorType.PAYMENT_CIRCUIT_OPEN, "현재 결제 시스템이 원활하지 않습니다. 잠시 후 다시 시도해주세요.");
    }

    // 콜백 처리는 멱등하게 동작해야 한다.
    // PG가 콜백을 여러 번 보낼 수 있고 (네트워크 재시도), 우리 서버가 재시작될 수도 있다.
    // Payment.markSuccess() / markFailed()에서 PENDING 상태가 아니면 예외를 던져
    // 중복 처리를 방지한다. (보상 트랜잭션도 멱등하게 — 이중 환불 방지와 같은 원리)
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
