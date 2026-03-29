package com.loopers.application.outbox;

import com.loopers.application.like.event.ProductLikedEvent;
import com.loopers.application.like.event.ProductUnlikedEvent;
import com.loopers.application.order.event.OrderCancelledEvent;
import com.loopers.application.order.event.OrderPlacedEvent;
import com.loopers.application.product.event.ProductPriceChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaOutboxEventListener {

    private final OutboxEventService outboxEventService;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderPlaced(OrderPlacedEvent event) {
        for (OrderPlacedEvent.OrderedItem item : event.items()) {
            outboxEventService.save(
                "PRODUCT",
                item.productId(),
                "ORDER_PLACED",
                Map.of(
                    "orderId", event.orderId(),
                    "userId", event.userId(),
                    "productId", item.productId(),
                    "quantity", item.quantity(),
                    "occurredAt", Instant.now().toString()
                )
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCancelled(OrderCancelledEvent event) {
        for (OrderCancelledEvent.CancelledItem item : event.items()) {
            outboxEventService.save(
                "PRODUCT",
                item.productId(),
                "ORDER_CANCELLED",
                Map.of(
                    "orderId", event.orderId(),
                    "userId", event.userId(),
                    "productId", item.productId(),
                    "quantity", item.quantity(),
                    "occurredAt", Instant.now().toString()
                )
            );
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProductLiked(ProductLikedEvent event) {
        outboxEventService.save(
            "PRODUCT",
            event.productId(),
            "PRODUCT_LIKED",
            Map.of(
                "productId", event.productId(),
                "occurredAt", Instant.now().toString()
            )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProductUnliked(ProductUnlikedEvent event) {
        outboxEventService.save(
            "PRODUCT",
            event.productId(),
            "PRODUCT_UNLIKED",
            Map.of(
                "productId", event.productId(),
                "occurredAt", Instant.now().toString()
            )
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleProductPriceChanged(ProductPriceChangedEvent event) {
        outboxEventService.save(
            "PRODUCT",
            event.productId(),
            "PRODUCT_PRICE_CHANGED",
            Map.of(
                "productId", event.productId(),
                "price", event.newPrice(),
                "occurredAt", event.occurredAt().toString()
            )
        );
    }
}
