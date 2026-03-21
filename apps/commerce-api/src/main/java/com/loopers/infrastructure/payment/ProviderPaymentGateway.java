package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;

import java.util.List;

public interface ProviderPaymentGateway {
    boolean supports(CardType cardType);

    PaymentGateway.PaymentGatewayTransaction requestPayment(PaymentGateway.PaymentGatewayRequest request);

    PaymentGateway.PaymentGatewayTransaction cancelPayment(PaymentGateway.PaymentGatewayCancelRequest request);

    PaymentGateway.PaymentGatewayTransaction getPayment(String memberId, String transactionKey);

    List<PaymentGateway.PaymentGatewayTransaction> getPaymentsByOrderId(String memberId, String orderReference);
}
