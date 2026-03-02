package com.loopers.domain.coupon.discount;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponType;

@Component
public class CouponDiscountProvider {

    private final Map<CouponType, CouponDiscountStrategy> strategyMap;

    public CouponDiscountProvider(List<CouponDiscountStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(CouponDiscountStrategy::getType, Function.identity()));
    }

    public CouponDiscountStrategy getStrategy(CouponType type) {
        CouponDiscountStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("지원하지 않는 쿠폰 타입입니다: " + type);
        }
        return strategy;
    }
}
