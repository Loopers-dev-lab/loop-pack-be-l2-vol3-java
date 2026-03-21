package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.vo.RefOrderId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentRepositoryImpl implements PaymentRepository {

    private final PaymentJpaRepository paymentJpaRepository;

    @Override
    public PaymentModel save(PaymentModel payment) {
        return paymentJpaRepository.save(payment);
    }

    @Override
    public Optional<PaymentModel> findById(Long id) {
        return paymentJpaRepository.findById(id);
    }

    @Override
    public Optional<PaymentModel> findByPgTransactionId(String pgTransactionId) {
        return paymentJpaRepository.findByPgTransactionId(pgTransactionId);
    }

    @Override
    public Optional<PaymentModel> findByRefOrderId(RefOrderId refOrderId) {
        return paymentJpaRepository.findByRefOrderId(refOrderId);
    }

    @Override
    public List<PaymentModel> findStaleRequested(ZonedDateTime cutoff) {
        return paymentJpaRepository.findStaleRequested(PaymentStatus.REQUESTED, cutoff);
    }

    @Override
    public List<PaymentModel> findExpiredCbFastFail(ZonedDateTime cutoff) {
        return paymentJpaRepository.findExpiredCbFastFail(PaymentStatus.PENDING, cutoff);
    }
}
