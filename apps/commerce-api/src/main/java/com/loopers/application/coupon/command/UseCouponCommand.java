package com.loopers.application.coupon.command;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.UUID;

public record UseCouponCommand(
        UUID couponId,
        String memberId,
        int orderAmount
) {
    public UseCouponCommand {
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }
        if (memberId == null || memberId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 식별자는 필수입니다.");
        }
        if (orderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 금액은 0 이상이어야 합니다.");
        }
    }
}
