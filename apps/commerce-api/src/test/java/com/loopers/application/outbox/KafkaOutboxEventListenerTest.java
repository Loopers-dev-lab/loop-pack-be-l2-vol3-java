package com.loopers.application.outbox;

import com.loopers.application.like.event.ProductLikedEvent;
import com.loopers.application.like.event.ProductUnlikedEvent;
import com.loopers.application.order.event.OrderCancelledEvent;
import com.loopers.application.order.event.OrderPlacedEvent;
import com.loopers.application.product.event.ProductPriceChangedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KafkaOutboxEventListenerTest {

    private OutboxEventService outboxEventService;
    private KafkaOutboxEventListener listener;

    @BeforeEach
    void setUp() {
        outboxEventService = mock(OutboxEventService.class);
        listener = new KafkaOutboxEventListener(outboxEventService);
    }

    @DisplayName("OrderPlacedEvent 수신 시 각 item별로 Outbox에 기록한다")
    @Test
    void handleOrderPlaced_savesPerItem() {
        OrderPlacedEvent event = new OrderPlacedEvent(1L, 100L, List.of(
            new OrderPlacedEvent.OrderedItem(10L, 2),
            new OrderPlacedEvent.OrderedItem(20L, 1)
        ));

        listener.handleOrderPlaced(event);

        verify(outboxEventService, times(2)).save(
            eq("PRODUCT"), anyLong(), eq("ORDER_PLACED"), any()
        );
        verify(outboxEventService).save(eq("PRODUCT"), eq(10L), eq("ORDER_PLACED"), any());
        verify(outboxEventService).save(eq("PRODUCT"), eq(20L), eq("ORDER_PLACED"), any());
    }

    @DisplayName("OrderCancelledEvent 수신 시 각 item별로 Outbox에 기록한다")
    @Test
    void handleOrderCancelled_savesPerItem() {
        OrderCancelledEvent event = new OrderCancelledEvent(1L, 100L, List.of(
            new OrderCancelledEvent.CancelledItem(10L, 2),
            new OrderCancelledEvent.CancelledItem(20L, 1)
        ));

        listener.handleOrderCancelled(event);

        verify(outboxEventService, times(2)).save(
            eq("PRODUCT"), anyLong(), eq("ORDER_CANCELLED"), any()
        );
        verify(outboxEventService).save(eq("PRODUCT"), eq(10L), eq("ORDER_CANCELLED"), any());
        verify(outboxEventService).save(eq("PRODUCT"), eq(20L), eq("ORDER_CANCELLED"), any());
    }

    @DisplayName("ProductLikedEvent 수신 시 Outbox에 기록한다")
    @Test
    void handleProductLiked_saves() {
        listener.handleProductLiked(new ProductLikedEvent(10L));

        verify(outboxEventService).save(eq("PRODUCT"), eq(10L), eq("PRODUCT_LIKED"), any());
    }

    @DisplayName("ProductUnlikedEvent 수신 시 Outbox에 기록한다")
    @Test
    void handleProductUnliked_saves() {
        listener.handleProductUnliked(new ProductUnlikedEvent(10L));

        verify(outboxEventService).save(eq("PRODUCT"), eq(10L), eq("PRODUCT_UNLIKED"), any());
    }

    @DisplayName("ProductPriceChangedEvent 수신 시 Outbox에 기록한다")
    @Test
    void handleProductPriceChanged_saves() {
        ProductPriceChangedEvent event = new ProductPriceChangedEvent(
            10L, BigDecimal.valueOf(15000), Instant.parse("2026-03-26T10:00:00Z")
        );

        listener.handleProductPriceChanged(event);

        verify(outboxEventService).save(eq("PRODUCT"), eq(10L), eq("PRODUCT_PRICE_CHANGED"), any());
    }
}
