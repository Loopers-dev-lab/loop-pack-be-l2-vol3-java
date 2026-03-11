package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.common.Name;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

/**
 * 발급된 쿠폰 도메인 객체 (템플릿 스냅샷)
 */
public class Coupon {

    private final Long id;
    private final Long refUserId;
    private final Long refTemplateId;
    private final Name name;
    private final DiscountType type;
    private final Integer discountValue;
    private final Money minOrderAmount;
    private final ZonedDateTime expiredAt;
    private CouponUsageType usageType;

    private Coupon(Long id, Long refUserId, Long refTemplateId, Name name, DiscountType type,
        Integer discountValue, Money minOrderAmount, ZonedDateTime expiredAt, CouponUsageType usageType) {
        this.id = id;
        this.refUserId = refUserId;
        this.refTemplateId = refTemplateId;
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.usageType = usageType;
    }

    public static Coupon issue(Long refUserId, CouponTemplate template) {
        if (refUserId == null || refUserId <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.USER_ID_INVALID);
        }
        return new Coupon(
            null, refUserId, template.getId(),
            template.getName(), template.getType(), template.getDiscountValue(),
            template.getMinOrderAmount(), template.getExpiredAt(),
            CouponUsageType.AVAILABLE
        );
    }

    public static Coupon restore(Long id, Long refUserId, Long refTemplateId, String name, DiscountType type,
        Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt, CouponUsageType usageType) {
        return new Coupon(id, refUserId, refTemplateId, new Name(name), type, discountValue, new Money(minOrderAmount), expiredAt, usageType);
    }

    /**
     * 쿠폰 사용 처리
     */
    public void use() {
        if (this.usageType == CouponUsageType.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.COUPON_ALREADY_USED);
        }
        this.usageType = CouponUsageType.USED;
    }

    /**
     * 쿠폰 사용 가능 여부 검증 (주문 시 사용)
     */
    public void validateUsable(Long userId, Money orderAmount, ZonedDateTime now) {
        if (!this.refUserId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.COUPON_NOT_OWNED);
        }
        if (this.usageType == CouponUsageType.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.COUPON_ALREADY_USED);
        }
        if (this.expiredAt.isBefore(now)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.COUPON_EXPIRED);
        }
        if (orderAmount.isLessThan(this.minOrderAmount)) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.MIN_ORDER_AMOUNT_NOT_MET);
        }
    }

    /**
     * 할인 금액 계산
     */
    public Money calculateDiscount(Money orderAmount) {
        return switch (this.type) {
            case FIXED -> new Money(this.discountValue);
            case RATE -> orderAmount.multiply(this.discountValue).divide(100);
        };
    }

    public Long getId() { return id; }
    public Long getRefUserId() { return refUserId; }
    public Long getRefTemplateId() { return refTemplateId; }
    public Name getName() { return name; }
    public DiscountType getType() { return type; }
    public Integer getDiscountValue() { return discountValue; }
    public Money getMinOrderAmount() { return minOrderAmount; }
    public ZonedDateTime getExpiredAt() { return expiredAt; }
    public CouponUsageType getUsageType() { return usageType; }
}
