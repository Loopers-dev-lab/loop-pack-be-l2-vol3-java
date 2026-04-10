package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueInfo;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponTemplateCommand;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.IssuedCouponStatus;

import java.time.ZonedDateTime;

public class CouponV1Dto {

    public record IssueResponse(
        Long requestId,
        Long couponId,
        Long userId,
        CouponIssueStatus status
    ) {
        public static IssueResponse from(CouponIssueInfo info) {
            return new IssueResponse(
                info.requestId(),
                info.couponId(),
                info.userId(),
                info.status()
            );
        }
    }

    public record CreateTemplateRequest(
        String name,
        CouponType type,
        Integer value,
        Integer minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        public CouponTemplateCommand toCommand() {
            return new CouponTemplateCommand(name, type, value, minOrderAmount, expiredAt);
        }
    }

    public record TemplateResponse(
        Long id,
        String name,
        CouponType type,
        Integer value,
        Integer minOrderAmount,
        ZonedDateTime expiredAt
    ) {
        public static TemplateResponse from(CouponInfo.TemplateInfo info) {
            return new TemplateResponse(
                info.id(), info.name(), info.type(),
                info.value(), info.minOrderAmount(), info.expiredAt()
            );
        }
    }

    public record IssuedCouponResponse(
        Long id,
        Long couponTemplateId,
        IssuedCouponStatus status
    ) {
        public static IssuedCouponResponse from(CouponInfo.IssuedInfo info) {
            return new IssuedCouponResponse(info.id(), info.couponTemplateId(), info.status());
        }
    }
}
