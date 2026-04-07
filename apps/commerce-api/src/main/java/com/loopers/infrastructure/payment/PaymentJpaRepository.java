package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 JPA Repository (06 §10.3).
 */
public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    Optional<PaymentModel> findFirstByOrderIdOrderByCreatedAtDescIdDesc(Long orderId);

    /**
     * 동일 주문 콜백 동시 처리 시 PENDING 행을 직렬화한다 (콜백 멱등·상태 전이 안정화).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    Optional<PaymentModel> findFirstByOrderIdAndStatusOrderByCreatedAtDescIdDesc(Long orderId, PaymentStatus status);

    @Query("SELECT p FROM PaymentModel p WHERE p.status = :status AND p.createdAt <= :cutoff ORDER BY p.createdAt ASC")
    List<PaymentModel> findStalePendingPayments(
            @Param("status") PaymentStatus status,
            @Param("cutoff") ZonedDateTime cutoff,
            Pageable pageable
    );
}
