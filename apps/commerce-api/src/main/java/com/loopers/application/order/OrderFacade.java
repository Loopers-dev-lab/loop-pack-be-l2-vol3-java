package com.loopers.application.order;

import com.loopers.application.coupon.CouponApp;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderApp orderApp;
    private final CouponApp couponApp;

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items, Long userCouponId) {
        BigDecimal discountAmount = BigDecimal.ZERO;
        Long refUserCouponId = null;

        if (userCouponId != null) {
            BigDecimal originalAmount = orderApp.calculateOriginalAmount(items);
            discountAmount = couponApp.calculateDiscount(userCouponId, memberId, originalAmount);
            refUserCouponId = couponApp.useUserCoupon(userCouponId);
        }

        return orderApp.createOrder(memberId, items, discountAmount, refUserCouponId);
    }

    @Transactional
    public OrderInfo cancelOrder(Long memberId, String orderId) {
        OrderInfo info = orderApp.cancelOrder(memberId, orderId);
        if (info.refUserCouponId() != null) {
            couponApp.restoreUserCoupon(info.refUserCouponId());
        }
        return info;
    }
}
