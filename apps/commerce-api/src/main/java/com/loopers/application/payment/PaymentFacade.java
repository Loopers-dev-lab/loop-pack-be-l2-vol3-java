package com.loopers.application.payment;

import com.loopers.infrastructure.payment.PgSimulatorClient;
import com.loopers.infrastructure.payment.PgSimulatorRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 결제 유스케이스 조율 (06 §10.2).
 * (1) DB 트랜잭션으로 PENDING 저장 (2) 트랜잭션 종료 후 PG 호출.
 */
@Service
public class PaymentFacade {

    private final PaymentPersistenceService persistenceService;
    private final PgSimulatorClient pgSimulatorClient;
    private final String callbackUrl;

    public PaymentFacade(PaymentPersistenceService persistenceService,
                         PgSimulatorClient pgSimulatorClient,
                         @Value("${pg.simulator.callback-url}") String callbackUrl) {
        this.persistenceService = persistenceService;
        this.pgSimulatorClient = pgSimulatorClient;
        this.callbackUrl = callbackUrl;
    }

    /**
     * 결제 요청. Phase 2: PENDING 저장 후 트랜잭션 밖에서 PG 호출.
     * Retry/CB/Fallback은 Phase 4~6에서 적용.
     */
    public PaymentInfo requestPayment(Long userId, Long orderId, String cardType, String cardNo) {
        PendingPaymentResult result = persistenceService.savePendingAndGetRequestParam(
                userId, orderId, cardType, cardNo, callbackUrl);
        // 트랜잭션 밖: PG 호출 (06 §5.1)
        PaymentRequestParam param = result.requestParam();
        PgSimulatorRequest request = new PgSimulatorRequest(
                param.orderId(),
                param.cardType(),
                param.cardNo(),
                param.amount(),
                param.callbackUrl()
        );
        pgSimulatorClient.requestPayment(request);
        // Phase 3에서 콜백으로 최종 처리. 여기서는 접수 응답만 반환
        return result.paymentInfo();
    }
}
