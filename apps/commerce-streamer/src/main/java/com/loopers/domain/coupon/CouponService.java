package com.loopers.domain.coupon;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class CouponService {

    private final CouponRepository couponRepository;

    @Transactional
    public CouponModel getByIdWithLock(Long id) {
        return couponRepository.findByIdWithLock(id)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 쿠폰입니다. id=" + id));
    }
}
