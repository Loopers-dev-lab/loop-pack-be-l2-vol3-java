package com.loopers.domain.metrics.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.metrics.MetricsEventType;
import com.loopers.domain.metrics.MetricsPayload;
import com.loopers.domain.metrics.ProductMetricsRepository;

@ExtendWith(MockitoExtension.class)
class OrderHandlerTest {

    @InjectMocks
    private OrderHandler orderHandler;

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @DisplayName("supports를 호출할 때,")
    @Nested
    class Supports {

        @DisplayName("ORDER_COMPLETED이면, true를 반환한다.")
        @Test
        void returnsTrue_whenOrderCompleted() {
            assertThat(orderHandler.supports(MetricsEventType.ORDER_COMPLETED)).isTrue();
        }

        @DisplayName("다른 타입이면, false를 반환한다.")
        @Test
        void returnsFalse_whenOtherType() {
            assertThat(orderHandler.supports(MetricsEventType.LIKED)).isFalse();
            assertThat(orderHandler.supports(MetricsEventType.PRODUCT_VIEWED)).isFalse();
        }
    }

    @DisplayName("handle을 호출할 때,")
    @Nested
    class Handle {

        @DisplayName("orderItems의 각 상품에 대해 upsertOrderCount를 호출한다.")
        @Test
        void upsertsOrderCountPerProduct() {
            // arrange
            List<MetricsPayload.Order.OrderItem> items = List.of(
                    new MetricsPayload.Order.OrderItem(10L, 2L),
                    new MetricsPayload.Order.OrderItem(20L, 3L)
            );

            // act
            orderHandler.handle(new MetricsPayload.Order(items));

            // assert
            then(productMetricsRepository).should().upsertOrderCount(10L, 2L);
            then(productMetricsRepository).should().upsertOrderCount(20L, 3L);
        }
    }
}
