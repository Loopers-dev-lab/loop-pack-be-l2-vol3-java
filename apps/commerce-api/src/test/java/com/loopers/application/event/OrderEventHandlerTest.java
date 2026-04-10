package com.loopers.application.event;

import com.loopers.domain.order.event.OrderCancelledEvent;
import com.loopers.domain.order.event.OrderCreatedEvent;
import com.loopers.domain.order.event.OrderExpiredEvent;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("OrderEventHandler 단위 테스트")
class OrderEventHandlerTest {

    private final OrderEventHandler handler = new OrderEventHandler();

    @Test
    @DisplayName("주문 생성 이벤트 처리 시 예외가 발생하지 않는다")
    void handleOrderCreated_ShouldNotThrow() {
        OrderCreatedEvent event = new OrderCreatedEvent(
                1L, 1L, OrderType.DIRECT, BigDecimal.valueOf(10000),
                LocalDateTime.now().plusMinutes(30), List.of());

        assertThatCode(() -> handler.handleOrderCreated(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주문 취소 이벤트 처리 시 예외가 발생하지 않는다")
    void handleOrderCancelled_ShouldNotThrow() {
        OrderCancelledEvent event = new OrderCancelledEvent(1L, 1L);

        assertThatCode(() -> handler.handleOrderCancelled(event))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("주문 만료 이벤트 처리 시 예외가 발생하지 않는다")
    void handleOrderExpired_ShouldNotThrow() {
        OrderExpiredEvent event = new OrderExpiredEvent(1L, 1L);

        assertThatCode(() -> handler.handleOrderExpired(event))
                .doesNotThrowAnyException();
    }
}
