package com.loopers.domain.coupon.discount;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponType;

/**
 * {@link CouponType}에 맞는 {@link CouponDiscountStrategy}를 제공하는 프로바이더.
 */
@Component
public class CouponDiscountProvider {

    private final Map<CouponType, CouponDiscountStrategy> strategyMap;

    public CouponDiscountProvider(List<CouponDiscountStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(CouponDiscountStrategy::getType, Function.identity()));
    }

    /**
     * 쿠폰 타입에 해당하는 할인 전략을 반환한다.
     *
     * @param type 쿠폰 타입
     * @return 해당 타입의 할인 전략
     * @throws IllegalArgumentException 지원하지 않는 쿠폰 타입인 경우
     */
    public CouponDiscountStrategy getStrategy(CouponType type) {
        CouponDiscountStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new IllegalArgumentException("지원하지 않는 쿠폰 타입입니다: " + type);
        }
        return strategy;
    }
}
