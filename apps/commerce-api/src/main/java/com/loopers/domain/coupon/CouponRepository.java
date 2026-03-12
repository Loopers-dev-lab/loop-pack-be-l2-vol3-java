package com.loopers.domain.coupon;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CouponRepository {

    CouponModel save(CouponModel couponModel);

    Optional<CouponModel> findById(Long id);

    Page<CouponModel> findAll(Pageable pageable);
}
