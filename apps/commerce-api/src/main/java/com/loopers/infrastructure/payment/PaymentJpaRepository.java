package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, UUID> {

    Optional<PaymentEntity> findByMemberIdAndOrderIdAndDeletedAtIsNull(String memberId, UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.memberId = :memberId and p.orderId = :orderId and p.deletedAt is null")
    Optional<PaymentEntity> findByMemberIdAndOrderIdForUpdate(
            @Param("memberId") String memberId,
            @Param("orderId") UUID orderId
    );

    Optional<PaymentEntity> findByMemberIdAndPgTransactionKeyAndDeletedAtIsNull(String memberId, String pgTransactionKey);

    List<PaymentEntity> findAllByOrderIdAndDeletedAtIsNull(UUID orderId);

    List<PaymentEntity> findAllByStatusInAndDeletedAtIsNull(List<PaymentStatus> statuses);
}
