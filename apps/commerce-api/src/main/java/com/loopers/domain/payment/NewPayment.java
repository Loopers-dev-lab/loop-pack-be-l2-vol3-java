package com.loopers.domain.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.shared.Money;

public record NewPayment(
        Long userId,
        Long orderId,
        CardType cardType,
        String cardNo,
        Money amount
) {

    public static NewPayment from(Order order, PaymentMethod paymentMethod) {
        return new NewPayment(
                order.getUserId(),
                order.getId(),
                paymentMethod.cardType(),
                paymentMethod.cardNo(),
                order.getTotalPrice()
        );
    }
}
