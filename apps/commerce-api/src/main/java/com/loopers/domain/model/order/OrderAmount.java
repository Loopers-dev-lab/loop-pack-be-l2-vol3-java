package com.loopers.domain.model.order;

import lombok.Getter;

@Getter
public class OrderAmount {

    private final PaymentMethod paymentMethod;
    private final Money totalAmount;
    private final Money discountAmount;
    private final Money paymentAmount;

    private OrderAmount(PaymentMethod paymentMethod, Money totalAmount,
                        Money discountAmount, Money paymentAmount) {
        if (paymentMethod == null) {
            throw new IllegalArgumentException("결제 수단은 필수입니다.");
        }
        if (totalAmount == null) {
            throw new IllegalArgumentException("총 금액은 필수입니다.");
        }
        this.paymentMethod = paymentMethod;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount != null ? discountAmount : Money.zero();
        this.paymentAmount = paymentAmount;
    }

    public static OrderAmount of(PaymentMethod paymentMethod, Money totalAmount,
                                 Money discountAmount) {
        if (totalAmount == null) {
            throw new IllegalArgumentException("총 금액은 필수입니다.");
        }
        Money discount = discountAmount != null ? discountAmount : Money.zero();
        Money payment = totalAmount.subtract(discount);
        return new OrderAmount(paymentMethod, totalAmount, discount, payment);
    }

    public static OrderAmount reconstitute(PaymentMethod paymentMethod, Money totalAmount,
                                           Money discountAmount, Money paymentAmount) {
        return new OrderAmount(paymentMethod, totalAmount, discountAmount, paymentAmount);
    }
}
