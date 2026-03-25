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
        boolean pgCalled = false;
        try {
            Payment payment = paymentService.findById(event.paymentId());

            // PG 결제 요청 (Resilience4j가 보호: 재시도, 서킷 브레이커, fallback)
            pgCalled = true;
            PgPaymentResponse pgResponse = pgClient.requestPayment(
                    event.userId(),
                    new PgPaymentRequest(
                            String.format("%06d", payment.getOrderId()),
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
            if (!pgCalled) {
                // PG 호출 전 내부 오류 → PG에 도달 불가능하므로 즉시 실패 처리 안전
                try {
                    resultHandler.handlePgFailed(event.paymentId(), event.orderId(),
                            "결제 처리 중 내부 오류: " + e.getMessage());
                } catch (Exception recoveryEx) {
                    log.error("결제 실패 처리도 실패 (폴링 스케줄러가 복구 예정): paymentId={}",
                            event.paymentId(), recoveryEx);
                }
            }
            // PG 호출 후 예외 → PG 상태 불확실 → PENDING 유지, 폴링 스케줄러가 PG 조회 후 확정
        }
    }
}
