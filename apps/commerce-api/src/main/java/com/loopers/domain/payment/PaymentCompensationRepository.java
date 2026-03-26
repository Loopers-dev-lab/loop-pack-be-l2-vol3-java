package com.loopers.domain.payment;

import com.loopers.support.enums.CompensationStatus;

import java.util.List;

/**
 * 결제 보정 리포지토리 인터페이스.
 */
public interface PaymentCompensationRepository {

    PaymentCompensationModel save(PaymentCompensationModel model);

    List<PaymentCompensationModel> findAllByStatus(CompensationStatus status);

    boolean existsByPaymentId(Long paymentId);
}
