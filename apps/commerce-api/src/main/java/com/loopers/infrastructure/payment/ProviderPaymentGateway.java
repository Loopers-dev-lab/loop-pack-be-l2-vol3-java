package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;

public interface ProviderPaymentGateway extends PaymentGateway {
    boolean supports(CardType cardType);
}
