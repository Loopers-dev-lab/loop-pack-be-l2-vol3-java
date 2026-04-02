package com.loopers.infrastructure.persistence.jpa.fcfs;

import com.loopers.domain.fcfs.CouponIssueRequest;
import com.loopers.domain.fcfs.CouponIssueRequestRepository;
import com.loopers.domain.fcfs.CouponIssueRequestStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * CouponIssueRequestRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        CouponIssueRequestJpaEntity entity = CouponIssueRequestMapper.toJpaEntity(request);
        CouponIssueRequestJpaEntity saved = jpaRepository.save(entity);
        return CouponIssueRequestMapper.toDomain(saved);
    }

    @Override
    public Optional<CouponIssueRequest> findByRequestId(String requestId) {
        return jpaRepository.findByRequestId(requestId)
            .map(CouponIssueRequestMapper::toDomain);
    }

    @Override
    public void updateStatus(String requestId, CouponIssueRequestStatus status, String failureReason) {
        jpaRepository.updateStatus(requestId, status, failureReason);
    }

    @Override
    public long countByStatus(CouponIssueRequestStatus status) {
        return jpaRepository.countByStatus(status);
    }

    @Override
    public long countByCouponIdAndStatus(Long couponId, CouponIssueRequestStatus status) {
        return jpaRepository.countByCouponIdAndStatus(couponId, status);
    }
}
