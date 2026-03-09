package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplateModel;
import com.loopers.domain.coupon.CouponTemplateProjection;
import com.loopers.domain.coupon.CouponTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository jpaRepository;

    public CouponTemplateRepositoryImpl(CouponTemplateJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<CouponTemplateModel> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<CouponTemplateModel> findByIdAndNotDeleted(Long id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public Page<CouponTemplateModel> findNotDeleted(Pageable pageable) {
        return jpaRepository.findByDeletedAtIsNull(pageable);
    }

    @Override
    public Page<CouponTemplateProjection> findNotDeletedAsProjection(Pageable pageable) {
        return jpaRepository.findProjectionByDeletedAtIsNull(pageable);
    }

    @Override
    public Optional<CouponTemplateProjection> findByIdAndNotDeletedAsProjection(Long id) {
        return jpaRepository.findProjectionByIdAndDeletedAtIsNull(id);
    }

    @Override
    public CouponTemplateModel save(CouponTemplateModel template) {
        return jpaRepository.save(template);
    }
}
