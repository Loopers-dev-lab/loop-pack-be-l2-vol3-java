package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCouponModel;
import com.loopers.domain.coupon.IssuedCouponProjection;
import com.loopers.domain.coupon.IssuedCouponStatus;

import java.time.ZonedDateTime;

/**
 * 발급 쿠폰(내 쿠폰) 응답용 애플리케이션 DTO.
 * 상태는 AVAILABLE / USED / EXPIRED.
 */
public record IssuedCouponInfo(
    Long id,
    Long couponId,
    String status,
    ZonedDateTime expiredAt,
    ZonedDateTime usedAt,
    ZonedDateTime createdAt
) {
    public static IssuedCouponInfo from(IssuedCouponModel model) {
        if (model == null) {
            return null;
        }
        IssuedCouponStatus status = model.getActualStatus(ZonedDateTime.now());
        return new IssuedCouponInfo(
            model.getId(),
            model.getCouponId(),
            status.name(),
            model.getExpiredAt(),
            model.getUsedAt(),
            model.getCreatedAt()
        );
    }

    /** 프로젝션 조회 결과를 Info로 변환. 조회 시점 기준 실질 상태(EXPIRED) 반영. */
    public static IssuedCouponInfo from(IssuedCouponProjection projection) {
        if (projection == null) {
            return null;
        }
        ZonedDateTime now = ZonedDateTime.now();
        IssuedCouponStatus status = projection.getStatus() == IssuedCouponStatus.USED
            ? IssuedCouponStatus.USED
            : (projection.getExpiredAt() != null && !now.isBefore(projection.getExpiredAt())
                ? IssuedCouponStatus.EXPIRED
                : IssuedCouponStatus.AVAILABLE);
        return new IssuedCouponInfo(
            projection.getId(),
            projection.getCouponId(),
            status.name(),
            projection.getExpiredAt(),
            projection.getUsedAt(),
            projection.getCreatedAt()
        );
    }
}
