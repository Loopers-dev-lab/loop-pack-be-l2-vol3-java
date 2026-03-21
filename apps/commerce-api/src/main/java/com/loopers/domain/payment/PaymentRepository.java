package com.loopers.domain.payment;

import com.loopers.domain.payment.vo.RefOrderId;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository {
    PaymentModel save(PaymentModel payment);
    Optional<PaymentModel> findById(Long id);
    Optional<PaymentModel> findByPgTransactionId(String pgTransactionId);
    Optional<PaymentModel> findByRefOrderId(RefOrderId refOrderId);
    List<PaymentModel> findStaleRequested(ZonedDateTime cutoff);
    List<PaymentModel> findExpiredCbFastFail(ZonedDateTime cutoff);
}
