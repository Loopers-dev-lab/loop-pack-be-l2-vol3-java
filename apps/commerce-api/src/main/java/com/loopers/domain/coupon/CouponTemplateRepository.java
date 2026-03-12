package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

/**
 * CouponTemplate Repository 인터페이스.
 */
public interface CouponTemplateRepository {

    CouponTemplate save(CouponTemplate template);

    Optional<CouponTemplate> findById(Long id);

    Optional<CouponTemplate> findByIdActive(Long id);

    Optional<CouponTemplate> findByIdWithLock(Long id);

    Page<CouponTemplate> findAllActive(Pageable pageable);
}
