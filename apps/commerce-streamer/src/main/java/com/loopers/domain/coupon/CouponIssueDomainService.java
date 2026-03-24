package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class CouponIssueDomainService {

    private final CouponIssueRepository couponIssueRepository;

    public CouponIssue issue(Coupon coupon, Long userId) {
        if (coupon.getDeletedAt() != null) {
            throw new CoreException(ErrorType.NOT_FOUND, "삭제된 쿠폰은 발급할 수 없습니다.");
        }
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰은 발급할 수 없습니다.");
        }
        return couponIssueRepository.save(new CouponIssue(coupon.getId(), userId));
    }
}
