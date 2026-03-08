package com.loopers.domain.coupon;

import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
public class CouponService {

    public CouponIssue createIssue(Long couponId, Long userId, ZonedDateTime expiredAt) {
        return CouponIssue.create(couponId, userId, expiredAt);
    }
}
