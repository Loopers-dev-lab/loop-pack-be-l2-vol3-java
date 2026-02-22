package com.loopers.infrastructure.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CouponTemplateRepositoryImpl implements CouponTemplateRepository {

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;

    public CouponTemplateRepositoryImpl(CouponTemplateJpaRepository couponTemplateJpaRepository) {
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
    }

    @Override
    public CouponTemplate save(CouponTemplate couponTemplate) {
        return couponTemplateJpaRepository.save(couponTemplate);
    }

    @Override
    public Optional<CouponTemplate> findById(Long id) {
        return couponTemplateJpaRepository.findById(id);
    }

    @Override
    public List<CouponTemplate> findAll(int page, int size) {
        return couponTemplateJpaRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        ).getContent();
    }

    @Override
    public long count() {
        return couponTemplateJpaRepository.count();
    }
}
