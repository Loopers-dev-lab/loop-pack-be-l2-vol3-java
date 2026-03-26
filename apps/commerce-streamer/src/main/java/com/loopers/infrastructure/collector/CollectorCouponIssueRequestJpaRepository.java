package com.loopers.infrastructure.collector;

import com.loopers.domain.collector.CollectorCouponIssueRequestModel;
import com.loopers.domain.collector.CollectorCouponIssueRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface CollectorCouponIssueRequestJpaRepository extends JpaRepository<CollectorCouponIssueRequestModel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM CollectorCouponIssueRequestModel r WHERE r.id = :id")
    Optional<CollectorCouponIssueRequestModel> findByIdForUpdate(Long id);

    long countByCouponIdAndStatus(Long couponId, CollectorCouponIssueRequestStatus status);
}
