package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.vo.RefOrderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByPgTransactionId(String pgTransactionId);

    Optional<PaymentModel> findByRefOrderId(RefOrderId refOrderId);

    @Query("SELECT p FROM PaymentModel p WHERE p.status = :status AND p.pgTransactionId IS NOT NULL AND p.updatedAt < :cutoff")
    List<PaymentModel> findStaleRequested(@Param("status") PaymentStatus status, @Param("cutoff") ZonedDateTime cutoff);

    @Query("SELECT p FROM PaymentModel p WHERE p.status = :status AND p.pgTransactionId IS NULL AND p.createdAt < :cutoff")
    List<PaymentModel> findExpiredCbFastFail(@Param("status") PaymentStatus status, @Param("cutoff") ZonedDateTime cutoff);
}
