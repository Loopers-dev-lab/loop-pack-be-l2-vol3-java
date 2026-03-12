package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.time.ZonedDateTime;

/**
 * 발급된 쿠폰 도메인 엔티티.
 * 사용자에게 발급된 쿠폰 인스턴스.
 */
public class IssuedCoupon {

    private Long id;
    private Long userId;
    private Long couponTemplateId;
    private IssuedCouponStatus status;
    private ZonedDateTime usedAt;
    private ZonedDateTime issuedAt;
    private ZonedDateTime expiredAt;

    private IssuedCoupon() {}

    /**
     * 새 쿠폰 발급.
     *
     * @param userId 사용자 ID
     * @param template 쿠폰 템플릿
     * @return 발급된 쿠폰
     */
    public static IssuedCoupon create(Long userId, CouponTemplate template) {
        IssuedCoupon coupon = new IssuedCoupon();
        coupon.userId = userId;
        coupon.couponTemplateId = template.getId();
        coupon.status = IssuedCouponStatus.AVAILABLE;
        coupon.usedAt = null;
        coupon.issuedAt = ZonedDateTime.now();
        coupon.expiredAt = template.getExpiredAt();
        return coupon;
    }

    /**
     * DB에서 복원 (Infrastructure에서 사용).
     */
    public static IssuedCoupon reconstitute(Long id, Long userId, Long couponTemplateId,
            IssuedCouponStatus status, ZonedDateTime usedAt,
            ZonedDateTime issuedAt, ZonedDateTime expiredAt) {
        IssuedCoupon coupon = new IssuedCoupon();
        coupon.id = id;
        coupon.userId = userId;
        coupon.couponTemplateId = couponTemplateId;
        coupon.status = status;
        coupon.usedAt = usedAt;
        coupon.issuedAt = issuedAt;
        coupon.expiredAt = expiredAt;
        return coupon;
    }

    /**
     * 사용 가능 여부.
     */
    public boolean isUsable() {
        return status == IssuedCouponStatus.AVAILABLE && !isExpired();
    }

    /**
     * 만료 여부.
     */
    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    /**
     * 표시 상태 조회.
     * USED > EXPIRED > AVAILABLE 우선순위로 결정.
     */
    public CouponDisplayStatus getDisplayStatus() {
        if (status == IssuedCouponStatus.USED) {
            return CouponDisplayStatus.USED;
        }
        if (isExpired()) {
            return CouponDisplayStatus.EXPIRED;
        }
        return CouponDisplayStatus.AVAILABLE;
    }

    /**
     * 쿠폰 사용.
     *
     * @throws CoreException 이미 사용되었거나 만료된 경우
     */
    public void use() {
        if (status == IssuedCouponStatus.USED) {
            throw new CoreException(ErrorType.COUPON_ALREADY_USED);
        }
        if (isExpired()) {
            throw new CoreException(ErrorType.COUPON_EXPIRED);
        }
        this.status = IssuedCouponStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    // Getters
    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getCouponTemplateId() {
        return couponTemplateId;
    }

    public IssuedCouponStatus getStatus() {
        return status;
    }

    public ZonedDateTime getUsedAt() {
        return usedAt;
    }

    public ZonedDateTime getIssuedAt() {
        return issuedAt;
    }

    public ZonedDateTime getExpiredAt() {
        return expiredAt;
    }
}
