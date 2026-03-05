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

    // 발급 시점 스냅샷 (어드민이 템플릿 수정해도 발급된 쿠폰에 영향 없음)
    private String couponName;
    private DiscountType discountType;
    private int discountValue;
    private Integer maxDiscountAmount;

    protected IssuedCoupon() {}

    private IssuedCoupon(Long couponTemplateId, Long userId,
                         String couponName, DiscountType discountType,
                         int discountValue, Integer maxDiscountAmount) {
        if (couponTemplateId == null || userId == null) {
            throw new CoreException(CouponErrorType.INVALID_COUPON_ISSUE);
        }
        this.couponTemplateId = couponTemplateId;
        this.userId = userId;
        this.status = IssuedCouponStatus.ISSUED;
        this.couponName = couponName;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public static IssuedCoupon create(Long couponTemplateId, Long userId,
                                       String couponName, DiscountType discountType,
                                       int discountValue, Integer maxDiscountAmount) {
        return new IssuedCoupon(couponTemplateId, userId, couponName, discountType,
                discountValue, maxDiscountAmount);
    }

    /**
     * DB에서 읽어온 데이터로 도메인 객체 재구성
     * Infrastructure 계층에서만 호출
     */
    public static IssuedCoupon reconstitute(Long id, Long couponTemplateId, Long userId,
                                             IssuedCouponStatus status, Long orderId, ZonedDateTime usedAt,
                                             ZonedDateTime createdAt, ZonedDateTime updatedAt,
                                             ZonedDateTime deletedAt,
                                             String couponName, DiscountType discountType,
                                             int discountValue, Integer maxDiscountAmount) {
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
        coupon.couponName = couponName;
        coupon.discountType = discountType;
        coupon.discountValue = discountValue;
        coupon.maxDiscountAmount = maxDiscountAmount;
        return coupon;
    }

    /**
     * 사용 가능 상태인지 검증 (POJO 빠른 실패)
     * 실제 상태 변경은 원자적 UPDATE(SQL)가 담당한다.
     */
    public void validateUsable() {
        if (this.status != IssuedCouponStatus.ISSUED) {
            throw new CoreException(CouponErrorType.INVALID_COUPON_STATUS);
        }
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

    public String getCouponName() {
        return this.couponName;
    }

    public DiscountType getDiscountType() {
        return this.discountType;
    }

    public int getDiscountValue() {
        return this.discountValue;
    }

    public Integer getMaxDiscountAmount() {
        return this.maxDiscountAmount;
    }
}
