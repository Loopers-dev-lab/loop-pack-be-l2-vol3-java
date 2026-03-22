package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;

/**
 * 결제 JPA Repository (06 §10.3).
 */
public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    java.util.Optional<PaymentModel> findFirstByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);

    @Query("SELECT p FROM PaymentModel p WHERE p.status = :status AND p.createdAt <= :cutoff ORDER BY p.createdAt ASC")
    List<PaymentModel> findStalePendingPayments(
            @Param("status") PaymentStatus status,
            @Param("cutoff") ZonedDateTime cutoff,
            Pageable pageable
    );
}
