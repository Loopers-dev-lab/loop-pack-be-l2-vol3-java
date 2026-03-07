package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {

    // Command

    Coupon save(Coupon coupon);
    int issueIfAvailable(Long id);

    // Query

    Optional<Coupon> findById(Long id);

    Optional<Coupon> findActiveById(Long id);

    Page<Coupon> findAllActive(Pageable pageable);
}
