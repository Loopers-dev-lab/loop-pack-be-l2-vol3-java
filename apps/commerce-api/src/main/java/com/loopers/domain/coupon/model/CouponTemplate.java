package com.loopers.domain.coupon.model;

import com.loopers.domain.coupon.vo.CouponName;
import com.loopers.domain.coupon.vo.DiscountValue;
import com.loopers.domain.coupon.vo.MinOrderAmount;
import com.loopers.support.CouponEnums;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class CouponTemplate {

    private Long id;
    private CouponName name;
    private CouponEnums.Type type;
    private DiscountValue discountValue;
    private MinOrderAmount minOrderAmount;
    private LocalDateTime expiredAt;

    private CouponTemplate(CouponName name, CouponEnums.Type type, DiscountValue discountValue,
                           MinOrderAmount minOrderAmount, LocalDateTime expiredAt) {
        this.name = name;
        this.type = type;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public static CouponTemplate create(CouponCommand.CreateTemplate command) {
        CouponEnums.Type type = command.type();
        DiscountValue discountValue = new DiscountValue(command.discountValue());

        validateDiscountValue(type, discountValue);
        validateExpiredAt(command.expiredAt());

        return new CouponTemplate(
                new CouponName(command.name()),
                type,
                discountValue,
                new MinOrderAmount(command.minOrderAmount()),
                command.expiredAt()
        );
    }

    public static CouponTemplate reconstruct(Long id, String name, String type, int discountValue,
                                              int minOrderAmount, LocalDateTime expiredAt) {
        CouponTemplate template = new CouponTemplate(
                new CouponName(name),
                CouponEnums.Type.valueOf(type),
                new DiscountValue(discountValue),
                new MinOrderAmount(minOrderAmount),
                expiredAt
        );
        template.id = id;
        return template;
    }

    public void update(CouponCommand.UpdateTemplate command) {
        CouponEnums.Type newType = command.type();
        DiscountValue newDiscountValue = new DiscountValue(command.discountValue());

        validateDiscountValue(newType, newDiscountValue);
        validateExpiredAt(command.expiredAt());

        this.name = new CouponName(command.name());
        this.type = newType;
        this.discountValue = newDiscountValue;
        this.minOrderAmount = new MinOrderAmount(command.minOrderAmount());
        this.expiredAt = command.expiredAt();
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }

    public int calculateDiscount(int orderAmount) {
        if (type == CouponEnums.Type.FIXED) {
            return Math.min(discountValue.value(), orderAmount);
        }
        return orderAmount * discountValue.value() / 100;
    }

    private static void validateDiscountValue(CouponEnums.Type type, DiscountValue discountValue) {
        if (type == CouponEnums.Type.RATE && discountValue.value() > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인 값은 100 이하여야 합니다.");
        }
    }

    private static void validateExpiredAt(LocalDateTime expiredAt) {
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
        if (expiredAt.isBefore(LocalDateTime.now())) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 현재 시각 이후여야 합니다.");
        }
    }
}
