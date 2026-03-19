package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByMemberIdAndOrderIdAndDeletedAtIsNull(String memberId, UUID orderId);

    Optional<PaymentEntity> findByMemberIdAndPgTransactionKeyAndDeletedAtIsNull(String memberId, String pgTransactionKey);

    List<PaymentEntity> findAllByOrderIdAndDeletedAtIsNull(UUID orderId);

    List<PaymentEntity> findAllByStatusInAndDeletedAtIsNull(List<PaymentStatus> statuses);
}
