package com.loopers.domain.payment;

import java.util.List;

public interface PaymentStatusHistoryRepository {
    PaymentStatusHistory save(PaymentStatusHistory history);
    List<PaymentStatusHistory> findAllByPaymentId(Long paymentId);
}
