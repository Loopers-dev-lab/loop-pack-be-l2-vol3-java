package com.loopers.infrastructure.payment.repository;

import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.payment.entity.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentEntity, Long> {

    Optional<PaymentEntity> findByOrderId(String orderId);

    Optional<PaymentEntity> findByTransactionKey(String transactionKey);

    @Modifying
    @Query("UPDATE PaymentEntity p SET p.transactionKey = :transactionKey, p.status = :status WHERE p.id = :id")
    int updateTransactionKeyAndStatus(@Param("id") Long id, @Param("transactionKey") String transactionKey, @Param("status") PaymentStatus status);

    @Modifying
    @Query("UPDATE PaymentEntity p SET p.status = :status, p.failReason = :failReason WHERE p.id = :id")
    int updateStatusAndFailReason(@Param("id") Long id, @Param("status") PaymentStatus status, @Param("failReason") String failReason);
}
