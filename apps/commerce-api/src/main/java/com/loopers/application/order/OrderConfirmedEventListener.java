package com.loopers.application.order;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.order.OrderConfirmedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConfirmedEventListener {

    private final CouponTemplateService couponTemplateService;
    private final CouponService couponService;

    @Value("${app.order-confirmed-coupon-template-id:1}")
    private Long couponTemplateId;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderConfirmedEvent event) {
        log.info("주문 확정 이벤트 수신. orderId={}, userId={}", event.getOrderId(), event.getUserId());

        CouponTemplate template = couponTemplateService.getById(couponTemplateId);
        couponService.issue(event.getUserId(), template);

        log.info("쿠폰 발급 완료. userId={}, templateId={}", event.getUserId(), couponTemplateId);
    }
}
