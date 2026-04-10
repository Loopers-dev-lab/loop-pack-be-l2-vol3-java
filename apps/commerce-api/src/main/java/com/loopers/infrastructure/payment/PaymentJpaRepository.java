package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentModel;
import com.loopers.support.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 결제 엔티티에 대한 Spring Data JPA Repository 인터페이스.
 */
public interface PaymentJpaRepository extends JpaRepository<PaymentModel, Long> {

    Optional<PaymentModel> findByTransactionKey(String transactionKey);

    List<PaymentModel> findAllByOrderId(Long orderId);

    boolean existsByOrderIdAndStatus(Long orderId, PaymentStatus status);

    /**
     * CAS(Compare-And-Set) 방식으로 결제 상태를 원자적으로 변경한다.
     *
     * @param paymentId 결제 ID
     * @param from      변경 전 기대 상태
     * @param to        변경 후 상태
     * @return 영향받은 행 수 (0이면 상태 변경 실패)
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE PaymentModel p SET p.status = :to, p.updatedAt = CURRENT_TIMESTAMP, " +
           "p.failureReason = :failureReason, " +
           "p.paidAt = CASE WHEN :to = 'SUCCESS' THEN CURRENT_TIMESTAMP ELSE p.paidAt END " +
           "WHERE p.paymentId = :paymentId AND p.status = :from")
    int casUpdateStatus(@Param("paymentId") Long paymentId,
                        @Param("from") PaymentStatus from,
                        @Param("to") PaymentStatus to,
                        @Param("failureReason") String failureReason);

    /**
     * REQUESTED 상태이고 생성 시각이 지정 시각 이전인 결제 목록을 조회한다 (폴링용).
     * 네이티브 쿼리 사용: NORMALIZE_UTC 설정에서 JPQL LocalDateTime 파라미터 타입 불일치 회피.
     *
     * @param before 기준 시각
     * @return 조회된 결제 목록
     */
    @Query(value = """
        SELECT * FROM payment
        WHERE status = 'REQUESTED'
          AND created_at < DATE_SUB(UTC_TIMESTAMP(), INTERVAL :minutesAgo MINUTE)
        """, nativeQuery = true)
    List<PaymentModel> findAllRequestedBeforeMinutesAgo(@Param("minutesAgo") int minutesAgo);
}
