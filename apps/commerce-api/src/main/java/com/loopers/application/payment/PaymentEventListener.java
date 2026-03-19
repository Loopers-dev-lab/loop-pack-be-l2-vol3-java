package com.loopers.application.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.payment.PgClient;
import com.loopers.domain.payment.PgPaymentRequest;
import com.loopers.domain.payment.PgPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@RequiredArgsConstructor
@Component
public class PaymentEventListener {

    private final PaymentService paymentService;
    private final PgClient pgClient;
    private final PaymentResultHandler resultHandler;

    @Value("${pg.callback-url}")
    private String callbackUrl;

    /**
     * Payment 저장 트랜잭션 커밋 후 PG API 호출.
     *
     * AFTER_COMMIT을 사용하는 이유:
     * - PG 호출(최대 3초) 동안 DB 커넥션을 점유하지 않음
     * - Payment가 DB에 확실히 저장된 후 PG 호출 (롤백 시 PG 호출 불필요)
     *
     * 결과 처리는 PaymentResultHandler(별도 빈)에 위임:
     * - AFTER_COMMIT은 트랜잭션 밖에서 실행되므로, 결과 처리에 새 트랜잭션 필요
     * - 같은 클래스 내부 호출(self-invocation)은 @Transactional이 무시되므로 별도 빈으로 분리
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentRequest(PaymentRequestEvent event) {
        try {
            Payment payment = paymentService.findById(event.paymentId());

            // PG 결제 요청 (Resilience4j가 보호: 재시도, 서킷 브레이커, fallback)
            PgPaymentResponse pgResponse = pgClient.requestPayment(
                    event.userId(),
                    new PgPaymentRequest(
                            String.valueOf(payment.getOrderId()),
                            payment.getCardType(),
                            payment.getCardNo(),
                            (long) payment.getAmount(),
                            callbackUrl
                    )
            );

            if (pgResponse.accepted()) {
                resultHandler.handlePgAccepted(payment.getId(), pgResponse.transactionKey());
            } else if (pgResponse.timeout()) {
                resultHandler.handlePgResponseTimeout(payment.getId(), event.orderId(), pgResponse.message());
            } else {
                resultHandler.handlePgFailed(payment.getId(), event.orderId(), pgResponse.message());
            }
        } catch (Exception e) {
            log.error("PG 결제 이벤트 처리 실패: paymentId={}, orderId={}", event.paymentId(), event.orderId(), e);
        }
    }
}
