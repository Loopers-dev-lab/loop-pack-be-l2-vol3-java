package com.loopers.infrastructure.coupon.repository.impl;

import com.loopers.domain.coupon.model.CouponIssueRequest;
import com.loopers.domain.coupon.model.CouponIssueStatus;
import com.loopers.domain.coupon.repository.CouponIssueRequestRepository;
import com.loopers.infrastructure.coupon.entity.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.repository.CouponIssueRequestJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        CouponIssueRequestEntity entity = jpaRepository.save(CouponIssueRequestEntity.toEntity(request));
        return entity.toModel();
    }

    @Override
    public Optional<CouponIssueRequest> findById(Long id) {
        return jpaRepository.findById(id).map(CouponIssueRequestEntity::toModel);
    }

    @Override
    public void updateStatus(Long id, CouponIssueStatus status) {
        CouponIssueRequestEntity entity = jpaRepository.findById(id)
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "존재하지 않는 발급 요청입니다."));
        entity.updateStatus(status);
    }

    @Override
    public long countByTemplateId(Long couponTemplateId) {
        return jpaRepository.countByCouponTemplateId(couponTemplateId);
    }

    @Override
    public boolean existsByTemplateIdAndMemberId(Long couponTemplateId, Long memberId) {
        return jpaRepository.existsByCouponTemplateIdAndMemberId(couponTemplateId, memberId);
    }
}
