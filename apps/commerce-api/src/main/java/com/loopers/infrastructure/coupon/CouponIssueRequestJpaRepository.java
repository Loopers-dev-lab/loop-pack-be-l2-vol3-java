package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequestEntity, UUID> {
    Optional<CouponIssueRequestEntity> findByRequestId(UUID requestId);

    Optional<CouponIssueRequestEntity> findByRequestIdAndMemberId(UUID requestId, String memberId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CouponIssueRequestEntity r
               set r.status = :processing
             where r.requestId = :requestId
               and r.status = :pending
            """)
    int markProcessing(@Param("requestId") UUID requestId, @Param("pending") CouponIssueRequestStatus pending, @Param("processing") CouponIssueRequestStatus processing);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CouponIssueRequestEntity r
               set r.status = :succeeded,
                   r.failureReason = null,
                   r.processedAt = :processedAt
             where r.requestId = :requestId
            """)
    int markSucceeded(@Param("requestId") UUID requestId, @Param("succeeded") CouponIssueRequestStatus succeeded, @Param("processedAt") LocalDateTime processedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CouponIssueRequestEntity r
               set r.status = :failedStatus,
                   r.failureReason = :failureReason,
                   r.processedAt = :processedAt
             where r.requestId = :requestId
            """)
    int markFailed(@Param("requestId") UUID requestId, @Param("failedStatus") CouponIssueRequestStatus failedStatus, @Param("failureReason") String failureReason, @Param("processedAt") LocalDateTime processedAt);
}
