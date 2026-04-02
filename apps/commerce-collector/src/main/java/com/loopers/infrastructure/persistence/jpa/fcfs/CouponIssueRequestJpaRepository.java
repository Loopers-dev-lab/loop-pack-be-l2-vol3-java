package com.loopers.infrastructure.persistence.jpa.fcfs;

import com.loopers.domain.fcfs.CouponIssueRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 쿠폰 발급 요청 JPA Repository (commerce-collector용).
 */
public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestJpaEntity, Long> {

    Optional<CouponIssueRequestJpaEntity> findByRequestId(String requestId);

    @Modifying
    @Query("UPDATE CouponIssueRequestJpaEntity r SET r.status = :status, r.failureReason = :failureReason WHERE r.requestId = :requestId")
    void updateStatus(
        @Param("requestId") String requestId,
        @Param("status") CouponIssueRequestStatus status,
        @Param("failureReason") String failureReason
    );

    long countByStatus(CouponIssueRequestStatus status);

    long countByCouponIdAndStatus(Long couponId, CouponIssueRequestStatus status);
}
