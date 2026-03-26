package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByIdAndDeletedAtIsNull(Long id);

    Optional<PaymentModel> findByOrderIdAndDeletedAtIsNull(Long orderId);

    Optional<PaymentModel> findByTransactionKeyAndDeletedAtIsNull(String transactionKey);

    List<PaymentModel> findAllByStatusAndDeletedAtIsNull(PaymentStatus status);

    @Modifying
    @Query("UPDATE PaymentModel p SET p.status = :newStatus " +
           "WHERE p.id = :paymentId AND p.status IN :allowedStatuses AND p.deletedAt IS NULL")
    int updateStatusConditionally(@Param("paymentId") Long paymentId,
                                  @Param("newStatus") PaymentStatus newStatus,
                                  @Param("allowedStatuses") List<PaymentStatus> allowedStatuses);
}
