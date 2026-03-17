package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

public class PaymentFixture {

    public static Payment createPayment(PaymentStatus status) {
        Payment payment = createPendingPayment();
        if (status != PaymentStatus.PENDING) {
            payment.update(status, "처리 완료");
        }
        return payment;
    }

    public static Payment createPendingPayment() {
        NewPayment newPayment = new NewPayment(
                1L, 100L,
                "txn-key-123",
                CardType.SHINHAN,
                "1234-5678-9012-3456",
                Money.wons(50000L)
        );
        return Payment.create(newPayment);
    }
}
