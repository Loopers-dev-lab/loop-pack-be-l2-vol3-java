package com.loopers.interfaces.api.coupon.dto;

import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.support.CouponEnums;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.LocalDateTime;

public record CreateCouponTemplateApiReqDto(
        @NotBlank String name,
        @NotNull CouponEnums.Type type,
        @Positive int value,
        @PositiveOrZero int minOrderAmount,
        @NotNull @Future LocalDateTime expiredAt
) {
    public CouponCommand.CreateTemplate toCommand() {
        return new CouponCommand.CreateTemplate(name, type, value, minOrderAmount, expiredAt);
    }
}
