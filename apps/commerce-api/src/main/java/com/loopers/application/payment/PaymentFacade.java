package com.loopers.application.payment;

import com.loopers.application.payment.pg.PgClient;
import com.loopers.application.payment.pg.PgPaymentDto;
import com.loopers.application.payment.pg.exception.PgBadRequestException;
import com.loopers.application.payment.pg.exception.PgCircuitOpenException;
import com.loopers.application.payment.pg.exception.PgConnectTimeoutException;
import com.loopers.application.payment.pg.exception.PgReadTimeoutException;
import com.loopers.application.payment.pg.exception.PgServerException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PaymentFacade {

    private final PaymentApp paymentApp;
    private final PgClient pgClient;

    @Value("${payment.pg.callback-url}")
    private String callbackUrl;

    public void processPayment(String orderId, String cardType, String cardNo, Long userId) {
        // 내부 결제 데이터 생성
        PaymentInfo paymentInfo = paymentApp.pay(orderId, cardType, cardNo, userId);

        // 외부 PG사 연동
        PgPaymentDto.PaymentRequest request = PgPaymentDto.PaymentRequest.of(paymentInfo, callbackUrl);
        try {
            PgPaymentDto.TransactionResponse response = pgClient.requestPayment(request);
            paymentApp.applyPgResponse(paymentInfo.orderId(), response.transactionKey(), response.status().name(), response.reason());
        } catch (PgReadTimeoutException e) {
            // 읽기 타임아웃: PG 수신 가능성 있음 → PENDING 유지, 콜백/Reconciliation으로 복구
        } catch (PgBadRequestException e) {
            // 잘못된 결제 요청 → 서버 측 버그 가능성, FAILED 저장
            paymentApp.applyPgResponse(paymentInfo.orderId(), null, "FAILED", e.getMessage());
            throw new CoreException(ErrorType.INTERNAL_ERROR, e.getMessage());
        } catch (PgServerException | PgConnectTimeoutException | PgCircuitOpenException e) {
            // PG 미처리 확정 → FAILED 저장
            paymentApp.applyPgResponse(paymentInfo.orderId(), null, "FAILED", e.getMessage());
            throw new CoreException(ErrorType.BAD_GATEWAY, e.getMessage());
        }
    }

    public PaymentInfo getPayment(String orderId, Long userId) {
        return paymentApp.getPayment(orderId, userId);
    }

    public void handleCallback(String transactionKey, String orderId, String status, String reason) {
        paymentApp.applyPgResponse(orderId, transactionKey, status, reason);
    }
}
