package com.loopers.domain.coupon;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface CouponRepository {
    CouponModel save(CouponModel coupon);
    Optional<CouponModel> findById(Long id);
    Page<CouponModel> findAll(Pageable pageable);
    List<CouponModel> findAllByIdIn(List<Long> ids);
    void delete(CouponModel coupon);
    Optional<CouponModel> findByIdWithLock(Long id);
}
