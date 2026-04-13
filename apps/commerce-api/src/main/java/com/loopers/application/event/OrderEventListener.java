package com.loopers.application.event;

import com.loopers.domain.coupon.service.CouponService;
import com.loopers.domain.event.OrderCreatedEvent;
import com.loopers.domain.event.OrderExpiredEvent;
import com.loopers.domain.order.model.OrderProduct;
import com.loopers.domain.order.service.OrderProductService;
import com.loopers.domain.outbox.model.OutboxEvent;
import com.loopers.domain.outbox.model.OutboxEventType;
import com.loopers.domain.outbox.repository.OutboxEventRepository;
import com.loopers.domain.product.service.ProductService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RequiredArgsConstructor
@Component
public class OrderEventListener {

    private final OutboxEventRepository outboxEventRepository;
    private final OrderProductService orderProductService;
    private final ProductService productService;
    private final CouponService couponService;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderCreated(OrderCreatedEvent event) {
        log.info("주문 생성 이벤트 - orderId: {}, memberId: {}, totalPrice: {}", event.orderId(), event.memberId(), event.totalPrice());
        Map<String, Object> payload = new HashMap<>();
        payload.put("eventId", UUID.randomUUID().toString());
        payload.put("eventType", "ORDER_CREATED");
        payload.put("orderId", event.orderId());
        payload.put("memberId", event.memberId());
        payload.put("totalPrice", event.totalPrice());
        payload.put("orderProducts", event.orderProducts());
        payload.put("version", System.currentTimeMillis());
        payload.put("createdAt", LocalDateTime.now().toString());
        outboxEventRepository.save(OutboxEvent.create(
                OutboxEventType.ORDER_CREATED,
                String.valueOf(event.orderId()),
                toJson(payload)
        ));
        log.info("유저 행동 로깅 - memberId: {}, action: ORDER_CREATE, targetId: {}", event.memberId(), event.orderId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderExpired(OrderExpiredEvent event) {
        log.info("주문 만료 처리 - orderId: {}", event.orderId());
        try {
            List<OrderProduct> products = orderProductService.findByOrderId(event.orderId());
            for (OrderProduct op : products) {
                productService.increaseStockAtomic(op.getProductId(), op.getQuantity().value());
            }
            if (event.userCouponId() != null) {
                couponService.restoreUserCoupon(event.userCouponId());
            }
            log.info("주문 만료 복원 완료 - orderId: {}, 재고 복원 {}건, 쿠폰 복원: {}",
                    event.orderId(), products.size(), event.userCouponId() != null);
        } catch (Exception e) {
            log.error("주문 만료 복원 실패 - orderId: {}, 수동 확인 필요", event.orderId(), e);
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("이벤트 직렬화 실패", e);
        }
    }
}
