package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

public class PaymentFixture {

    public static Payment createPayment(PaymentStatus status) {
        switch (status) {
            case READY:
                return createReadyPayment();
            case PENDING:
                return createPendingPayment();
            case SUCCESS:
                Payment successPayment = createPendingPayment();
                successPayment.success("처리 완료");
                return successPayment;
            case FAILED:
                Payment failedPayment = createPendingPayment();
                failedPayment.fail("처리 완료");
                return failedPayment;
            default:
                throw new IllegalArgumentException("지원하지 않는 결제 상태: " + status);
        }
    }

    public static Payment createReadyPayment() {
        NewPayment newPayment = new NewPayment(
                1L, 100L,
                CardType.SHINHAN,
                "1234-5678-9012-3456",
                Money.wons(50000L)
        );
        return Payment.create(newPayment);
    }

    public static Payment createPendingPayment() {
        Payment payment = createReadyPayment();
        payment.confirmPayment("txn-key-123");
        return payment;
    }

    public static Payment createPendingPaymentWithTransactionKey(String transactionKey) {
        Payment payment = createReadyPayment();
        payment.confirmPayment(transactionKey);
        return payment;
    }
}
