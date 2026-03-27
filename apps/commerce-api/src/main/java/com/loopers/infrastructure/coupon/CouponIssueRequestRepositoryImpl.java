package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository couponIssueRequestJpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        Optional<CouponIssueRequestEntity> existing = couponIssueRequestJpaRepository.findByRequestId(request.requestId());
        if (existing.isPresent()) {
            CouponIssueRequestEntity entity = existing.get();
            entity.updateFrom(request);
            return couponIssueRequestJpaRepository.save(entity).toDomain();
        }
        return couponIssueRequestJpaRepository.save(CouponIssueRequestEntity.from(request)).toDomain();
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestIdAndMemberId(UUID requestId, String memberId) {
        return couponIssueRequestJpaRepository.findByRequestIdAndMemberId(requestId, memberId).map(CouponIssueRequestEntity::toDomain);
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(UUID requestId) {
        return couponIssueRequestJpaRepository.findByRequestId(requestId).map(CouponIssueRequestEntity::toDomain);
    }

    @Override
    public int markProcessing(UUID requestId) {
        return couponIssueRequestJpaRepository.markProcessing(requestId, CouponIssueRequestStatus.PENDING, CouponIssueRequestStatus.PROCESSING);
    }

    @Override
    public int markSucceeded(UUID requestId, LocalDateTime processedAt) {
        return couponIssueRequestJpaRepository.markSucceeded(requestId, CouponIssueRequestStatus.SUCCEEDED, processedAt);
    }

    @Override
    public int markFailed(UUID requestId, CouponIssueRequestStatus status, String failureReason, LocalDateTime processedAt) {
        return couponIssueRequestJpaRepository.markFailed(requestId, status, failureReason, processedAt);
    }
}
