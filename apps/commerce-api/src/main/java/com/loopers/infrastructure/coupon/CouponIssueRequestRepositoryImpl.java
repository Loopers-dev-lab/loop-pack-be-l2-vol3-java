package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CouponIssueRequestRepositoryImpl implements CouponIssueRequestRepository {

    private final CouponIssueRequestJpaRepository jpaRepository;

    @Override
    public CouponIssueRequest save(CouponIssueRequest request) {
        CouponIssueRequestEntity entity = CouponIssueRequestEntity.create(request);
        return jpaRepository.save(entity).toDomain();
    }

    @Override
    public Optional<CouponIssueRequest> findById(Long id) {
        return jpaRepository.findById(id).map(CouponIssueRequestEntity::toDomain);
    }
}
