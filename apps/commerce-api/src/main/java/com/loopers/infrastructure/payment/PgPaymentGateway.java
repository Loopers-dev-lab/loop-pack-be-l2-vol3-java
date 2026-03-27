package com.loopers.infrastructure.payment;

import com.loopers.infrastructure.payment.dto.PgPaymentRequest;
import com.loopers.infrastructure.payment.dto.PgPaymentResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class PgPaymentGateway {

    private final PgClient pgClient;

    @Bulkhead(name = "pg")
    @Retry(name = "pg")
    public PgPaymentResponse requestPayment(PgPaymentRequest request, String userId) {
        PgPaymentResponse response = pgClient.requestPayment(request, userId);
        if (response == null || response.transactionKey() == null) {
            throw new CoreException(ErrorType.PG_REQUEST_FAILED, "PG 응답이 유효하지 않습니다.");
        }
        return response;
    }

    public PgPaymentResponse getPaymentStatus(String transactionKey, String userId) {
        return pgClient.getPaymentStatus(transactionKey, userId);
    }
}
