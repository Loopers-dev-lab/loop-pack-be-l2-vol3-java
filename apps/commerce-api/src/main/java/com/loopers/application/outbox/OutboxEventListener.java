package com.loopers.application.outbox;

import com.loopers.application.like.LikeCancelledEvent;
import com.loopers.application.like.LikeCreatedEvent;
import com.loopers.application.order.OrderCreatedEvent;
import com.loopers.application.payment.PaymentCompletedEvent;
import com.loopers.confg.kafka.message.CatalogEventMessage;
import com.loopers.confg.kafka.message.OrderEventMessage;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.outbox.OutboxEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@RequiredArgsConstructor
@Component
public class OutboxEventListener {

    private static final String CATALOG_TOPIC = "catalog-events";
    private static final String ORDER_TOPIC = "order-events";

    private final OutboxEventService outboxEventService;
    private final OrderService orderService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLikeCreated(LikeCreatedEvent event) {
        long now = System.currentTimeMillis();
        CatalogEventMessage message = new CatalogEventMessage(
                UUID.randomUUID().toString(),
                "LIKE_CREATED",
                event.productId(),
                null,
                now,
                now
        );
        outboxEventService.save(message.eventId(), CATALOG_TOPIC, event.productId().toString(), message);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onLikeCancelled(LikeCancelledEvent event) {
        long now = System.currentTimeMillis();
        CatalogEventMessage message = new CatalogEventMessage(
                UUID.randomUUID().toString(),
                "LIKE_CANCELLED",
                event.productId(),
                null,
                now,
                now
        );
        outboxEventService.save(message.eventId(), CATALOG_TOPIC, event.productId().toString(), message);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onOrderCreated(OrderCreatedEvent event) {
        long now = System.currentTimeMillis();
        List<OrderItem> orderItems = orderService.getOrderItems(event.orderId());
        List<OrderEventMessage.Item> items = orderItems.stream()
                .map(i -> new OrderEventMessage.Item(i.getProductId(), i.getQuantity(), i.getPrice()))
                .toList();
        OrderEventMessage message = new OrderEventMessage(
                UUID.randomUUID().toString(),
                "ORDER_CREATED",
                event.orderId(),
                event.userId(),
                items,
                now,
                now
        );
        outboxEventService.save(message.eventId(), ORDER_TOPIC, event.orderId().toString(), message);
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        long now = System.currentTimeMillis();
        List<OrderItem> orderItems = orderService.getOrderItems(event.orderId());
        List<OrderEventMessage.Item> items = orderItems.stream()
                .map(i -> new OrderEventMessage.Item(i.getProductId(), i.getQuantity(), i.getPrice()))
                .toList();
        OrderEventMessage message = new OrderEventMessage(
                UUID.randomUUID().toString(),
                "ORDER_COMPLETED",
                event.orderId(),
                event.userId(),
                items,
                now,
                now
        );
        outboxEventService.save(message.eventId(), ORDER_TOPIC, event.orderId().toString(), message);
    }
}
