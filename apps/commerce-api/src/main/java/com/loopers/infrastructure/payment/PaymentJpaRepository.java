package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByIdAndDeletedAtIsNull(Long id);

    Optional<PaymentModel> findByOrderIdAndDeletedAtIsNull(Long orderId);

    Optional<PaymentModel> findByPgPaymentKeyAndDeletedAtIsNull(String pgPaymentKey);

    @Query("""
        SELECT p
        FROM PaymentModel p
        WHERE p.deletedAt IS NULL
          AND p.status IN :statuses
          AND (p.lastSyncedAt IS NULL OR p.lastSyncedAt <= :threshold)
        ORDER BY p.id ASC
        """)
    List<PaymentModel> findRecoverTargets(List<PaymentStatus> statuses, ZonedDateTime threshold, Pageable pageable);
}
