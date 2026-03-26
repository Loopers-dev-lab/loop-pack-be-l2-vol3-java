package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.ExternalPaymentClient;
import com.loopers.domain.payment.ExternalPaymentResponse;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("test")
@Component
public class StubExternalPaymentClient implements ExternalPaymentClient {

    @Override
    public void requestPayment(Long orderId, CardType cardType, String cardNo, long amount, String callbackUrl) {
        // Stub: 실제 PG 호출 없이 아무것도 하지 않음 (콜백은 테스트에서 직접 호출)
    }

    @Override
    public ExternalPaymentResponse getPaymentByOrderId(Long orderId) {
        return new ExternalPaymentResponse(UUID.randomUUID().toString(), true);
    }
}