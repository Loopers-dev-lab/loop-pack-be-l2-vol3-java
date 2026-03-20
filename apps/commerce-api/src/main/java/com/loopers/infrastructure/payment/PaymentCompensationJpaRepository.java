package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentCompensationModel;
import com.loopers.support.enums.CompensationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 결제 보정 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 */
public interface PaymentCompensationJpaRepository extends JpaRepository<PaymentCompensationModel, Long> {

    List<PaymentCompensationModel> findAllByStatus(CompensationStatus status);

    boolean existsByPaymentId(Long paymentId);
}
