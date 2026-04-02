package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.common.Name;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import com.loopers.support.error.ErrorType;
import java.time.ZonedDateTime;

/**
 * 쿠폰 템플릿 도메인 객체
 */
public class CouponTemplate {

    private final Long id;
    private Name name;
    private DiscountType type;
    private Integer discountValue; // FIXED: 할인 금액, RATE: 할인율(%)
    private Money minOrderAmount;
    private ZonedDateTime expiredAt;
    private final Integer maxIssuanceCount; // null이면 무제한
    private final Integer issuedCount;

    private CouponTemplate(Long id, Name name, DiscountType type, Integer discountValue, Money minOrderAmount, ZonedDateTime expiredAt, Integer maxIssuanceCount, Integer issuedCount) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.maxIssuanceCount = maxIssuanceCount;
        this.issuedCount = issuedCount;
    }

    public static CouponTemplate create(String name, DiscountType discountType, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt, Integer maxIssuanceCount) {
        validateDiscountType(discountType);
        validateExpiredAt(expiredAt);

        return new CouponTemplate(null, new Name(name), discountType, discountValue, new Money(minOrderAmount), expiredAt, maxIssuanceCount, 0);
    }

    public CouponTemplate updateTemplate(String name, DiscountType discountType, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt) {
        validateDiscountType(discountType);
        validateExpiredAt(expiredAt);

        return new CouponTemplate(this.id, new Name(name), discountType, discountValue, new Money(minOrderAmount), expiredAt, this.maxIssuanceCount, this.issuedCount);
    }

    public static CouponTemplate restore(Long id, String name, DiscountType type, Integer discountValue, Integer minOrderAmount, ZonedDateTime expiredAt, Integer maxIssuanceCount, Integer issuedCount) {
        return new CouponTemplate(id, new Name(name), type, discountValue, new Money(minOrderAmount), expiredAt, maxIssuanceCount, issuedCount);
    }

    private static void validateDiscountType(DiscountType discountType) {
        if (discountType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.DISCOUNT_TYPE_REQUIRED);
        }
    }

    private static void validateExpiredAt(ZonedDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.EXPIRED_AT_REQUIRED);
        }
        if (expiredAt.isBefore(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, ErrorMessage.Coupon.EXPIRED_AT_MUST_BE_FUTURE);
        }
    }

    public Long getId() { return id; }
    public Name getName() { return name; }
    public DiscountType getType() { return type; }
    public Integer getDiscountValue() { return discountValue; }
    public Money getMinOrderAmount() { return minOrderAmount; }
    public ZonedDateTime getExpiredAt() { return expiredAt; }
    public Integer getMaxIssuanceCount() { return maxIssuanceCount; }
    public Integer getIssuedCount() { return issuedCount; }
    public boolean hasIssuanceLimit() { return maxIssuanceCount != null; }
}
