package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import java.time.ZonedDateTime;

/**
 * IssuedCoupon Domain POJO (순수 도메인 객체)
 * JPA 의존성 없음
 */
public class IssuedCoupon {

    private Long id;
    private Long couponTemplateId;
    private Long userId;
    private IssuedCouponStatus status;
    private Long orderId;
    private ZonedDateTime usedAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime deletedAt;

    protected IssuedCoupon() {}

    private IssuedCoupon(Long couponTemplateId, Long userId) {
        if (couponTemplateId == null || userId == null) {
            throw new CoreException(CouponErrorType.INVALID_COUPON_ISSUE);
        }
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = IssuedCouponStatus.ISSUED;
    }

    public static IssuedCoupon create(Long couponTemplateId, Long userId) {
        return new IssuedCoupon(couponTemplateId, userId);
    }

    /**
     * DB에서 읽어온 데이터로 도메인 객체 재구성
     * Infrastructure 계층에서만 호출
     */
    public static IssuedCoupon reconstitute(Long id, Long couponTemplateId, Long userId,
                                             IssuedCouponStatus status, Long orderId, ZonedDateTime usedAt,
                                             ZonedDateTime createdAt, ZonedDateTime updatedAt,
                                             ZonedDateTime deletedAt) {
        IssuedCoupon coupon = new IssuedCoupon();
        coupon.id = id;
        coupon.couponTemplateId = couponTemplateId;
        coupon.userId = userId;
        coupon.status = status;
        coupon.orderId = orderId;
        coupon.usedAt = usedAt;
        coupon.createdAt = createdAt;
        coupon.updatedAt = updatedAt;
        coupon.deletedAt = deletedAt;
        return coupon;
    }

    public void use(Long orderId) {
        if (this.status != IssuedCouponStatus.ISSUED) {
            throw new CoreException(CouponErrorType.INVALID_COUPON_STATUS);
        }
        this.status = IssuedCouponStatus.USED;
        this.orderId = orderId;
        this.usedAt = ZonedDateTime.now();
    }

    public void expire() {
        this.status = IssuedCouponStatus.EXPIRED;
    }

    public void validateOwnership(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(CouponErrorType.NOT_OWNER);
        }
    }

    public Long getCouponTemplateId() {
        return this.couponTemplateId;
    }

    public Long getUserId() {
        return this.userId;
    }

    public IssuedCouponStatus getStatus() {
        return this.status;
    }

    public Long getOrderId() {
        return this.orderId;
    }

    public ZonedDateTime getUsedAt() {
        return this.usedAt;
    }

    public Long getId() {
        return this.id;
    }

    public ZonedDateTime getCreatedAt() {
        return this.createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return this.updatedAt;
    }

    public ZonedDateTime getDeletedAt() {
        return this.deletedAt;
    }
}
