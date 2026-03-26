package com.loopers.domain.order.event;

import com.loopers.application.order.OrderInfo;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderCreatedEvent 단위 테스트")
class OrderCreatedEventTest {

    @Test
    @DisplayName("OrderInfo로부터 OrderCreatedEvent가 올바르게 생성된다")
    void from_WithOrderInfo_ShouldCreateEvent() {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(30);

        OrderInfo.OrderItemInfo itemInfo = OrderInfo.OrderItemInfo.builder()
                .orderId(1L)
                .orderItemSeq(1)
                .productId(100L)
                .quantity(2)
                .snapshotProductName("테스트상품")
                .snapshotUnitPrice(BigDecimal.valueOf(10000))
                .snapshotBrandId("brand-1")
                .snapshotBrandName("테스트브랜드")
                .originalAmount(BigDecimal.valueOf(20000))
                .discountAmount(BigDecimal.ZERO)
                .finalAmount(BigDecimal.valueOf(20000))
                .build();

        OrderInfo orderInfo = OrderInfo.builder()
                .orderId(1L)
                .userId(10L)
                .orderType(OrderType.DIRECT)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(20000))
                .expiresAt(expiresAt)
                .items(List.of(itemInfo))
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.from(orderInfo);

        assertThat(event.orderId()).isEqualTo(1L);
        assertThat(event.userId()).isEqualTo(10L);
        assertThat(event.orderType()).isEqualTo(OrderType.DIRECT);
        assertThat(event.totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
        assertThat(event.expiresAt()).isEqualTo(expiresAt);
        assertThat(event.items()).hasSize(1);
        assertThat(event.items().get(0).productId()).isEqualTo(100L);
        assertThat(event.items().get(0).quantity()).isEqualTo(2);
        assertThat(event.items().get(0).finalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
    }

    @Test
    @DisplayName("items가 null인 OrderInfo에서 빈 리스트로 변환된다")
    void from_WithNullItems_ShouldCreateEmptyList() {
        OrderInfo orderInfo = OrderInfo.builder()
                .orderId(1L)
                .userId(10L)
                .orderType(OrderType.CART)
                .status(OrderStatus.PENDING_PAYMENT)
                .totalAmount(BigDecimal.valueOf(10000))
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .items(null)
                .build();

        OrderCreatedEvent event = OrderCreatedEvent.from(orderInfo);

        assertThat(event.items()).isEmpty();
    }
}
