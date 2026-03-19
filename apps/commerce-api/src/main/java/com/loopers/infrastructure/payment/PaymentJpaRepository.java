package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentJpaRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderIdAndDeletedAtIsNull(Long orderId);

    Optional<Payment> findByTransactionKeyAndDeletedAtIsNull(String transactionKey);

    List<Payment> findAllByStatusAndCreatedAtBeforeAndDeletedAtIsNull(
            PaymentStatus status, ZonedDateTime before);

    // clearAutomatically = true: UPDATE 후 1차 캐시 비움 → 이후 findById가 항상 최신 DB 상태를 반환
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, p.transactionKey = :transactionKey, p.pgRespondedAt = :pgRespondedAt " +
           "WHERE p.id = :id AND p.status = :pendingStatus")
    int markSuccessIfPending(@Param("id") Long id,
                             @Param("transactionKey") String transactionKey,
                             @Param("pgRespondedAt") ZonedDateTime pgRespondedAt,
                             @Param("newStatus") PaymentStatus newStatus,
                             @Param("pendingStatus") PaymentStatus pendingStatus);

    // transactionKey는 null일 수 있음 (PG 접수 실패 케이스): COALESCE로 기존 값 유지
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, " +
           "p.transactionKey = COALESCE(:transactionKey, p.transactionKey), " +
           "p.failureReason = :failureReason, p.pgRespondedAt = :pgRespondedAt " +
           "WHERE p.id = :id AND p.status = :pendingStatus")
    int markFailedIfPending(@Param("id") Long id,
                            @Param("transactionKey") String transactionKey,
                            @Param("failureReason") String failureReason,
                            @Param("pgRespondedAt") ZonedDateTime pgRespondedAt,
                            @Param("newStatus") PaymentStatus newStatus,
                            @Param("pendingStatus") PaymentStatus pendingStatus);

    // 타임아웃: pgRespondedAt 미설정 (PG가 응답하지 않았으므로)
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Payment p SET p.status = :newStatus, p.failureReason = :failureReason " +
           "WHERE p.id = :id AND p.status = :pendingStatus")
    int markTimeoutIfPending(@Param("id") Long id,
                             @Param("failureReason") String failureReason,
                             @Param("newStatus") PaymentStatus newStatus,
                             @Param("pendingStatus") PaymentStatus pendingStatus);
}
