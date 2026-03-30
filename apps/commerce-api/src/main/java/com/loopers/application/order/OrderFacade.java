package com.loopers.application.order;

import com.loopers.application.coupon.CouponApp;
import com.loopers.application.queue.QueueApp;
import com.loopers.config.QueueProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFacade {

    private final OrderApp orderApp;
    private final CouponApp couponApp;
    private final QueueApp queueApp;
    private final QueueProperties queueProperties;

    @Transactional
    public OrderInfo createOrder(Long memberId, List<OrderItemCommand> items, Long userCouponId, String entryToken) {
        if (queueProperties.enabled()) {
            queueApp.validateToken(memberId, entryToken);
        }

        BigDecimal discountAmount = BigDecimal.ZERO;
        Long refUserCouponId = null;

        if (userCouponId != null) {
            BigDecimal originalAmount = orderApp.calculateOriginalAmount(items);
            discountAmount = couponApp.calculateDiscount(userCouponId, memberId, originalAmount);
            refUserCouponId = couponApp.useUserCoupon(userCouponId);
        }

        OrderInfo orderInfo = orderApp.createOrder(memberId, items, discountAmount, refUserCouponId);

        if (queueProperties.enabled()) {
            queueApp.consumeToken(memberId);
        }

        return orderInfo;
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
