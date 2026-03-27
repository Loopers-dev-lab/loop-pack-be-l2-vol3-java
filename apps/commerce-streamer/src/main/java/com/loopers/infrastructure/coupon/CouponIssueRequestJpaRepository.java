package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CouponIssueRequestJpaRepository extends JpaRepository<CouponIssueRequest, Long> {

    Optional<CouponIssueRequest> findByEventId(String eventId);

    @Modifying
    @Query(value = "UPDATE coupons SET issued_count = issued_count + 1 " +
            "WHERE id = :couponId AND issued_count < max_issue_count " +
            "AND expired_at > NOW() AND deleted_at IS NULL",
            nativeQuery = true)
    int issueIfAvailable(@Param("couponId") Long couponId);
}
