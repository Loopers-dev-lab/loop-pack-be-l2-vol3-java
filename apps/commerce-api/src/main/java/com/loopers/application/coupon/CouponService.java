package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponRepository couponRepository;

    // Command

    @Transactional
    public Coupon register(CouponCommand.Register command) {
        Coupon coupon = Coupon.create(
                command.name(),
                command.type(),
                command.value(),
                command.minOrderAmount(),
                command.maxIssueCount(),
                command.expiredAt()
        );
        return couponRepository.save(coupon);
    }
}
